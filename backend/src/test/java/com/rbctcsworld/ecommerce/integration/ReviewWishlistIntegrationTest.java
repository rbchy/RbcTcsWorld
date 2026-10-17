package com.rbctcsworld.ecommerce.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Module 5: reviews, ratings, moderation and wishlist through the real API + database checks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewWishlistIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;
    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        admin = token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}")));
        productId = newProduct("20.00", 10);
    }

    // ---------- reviews ----------

    @Test
    void verifiedBuyersReviewAndTheAverageIsKeptOnTheProduct() throws Exception {
        String a = buyerWhoReceived();
        String b = buyerWhoReceived();

        review(a, 5, "Love it", "Works great").andExpect(status().isCreated())
                .andExpect(jsonPath("$.verifiedPurchase").value(true))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        review(b, 4, null, null).andExpect(status().isCreated());

        mvc.perform(get("/api/products/" + productId + "/reviews?sort=lowest"))     // public, no token
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.distribution['5']").value(1))
                .andExpect(jsonPath("$.distribution['4']").value(1))
                .andExpect(jsonPath("$.distribution['1']").value(0))
                .andExpect(jsonPath("$.reviews[*].rating").value(contains(4, 5)));

        mvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.ratingAverage").value(4.5))
                .andExpect(jsonPath("$.ratingCount").value(2));
        assertThat(dbRating()).isEqualByComparingTo("4.5");
    }

    @Test
    void publicReviewListNeverShowsEmailAddresses() throws Exception {
        review(buyerWhoReceived(), 3, "ok", "fine").andExpect(status().isCreated());
        String body = mvc.perform(get("/api/products/" + productId + "/reviews"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("@", "userId", "password");
        assertThat(body).contains("\"reviewer\":\"rv***\"");
    }

    @Test
    void onlyCustomersWhoReceivedTheProductMayReview() throws Exception {
        review(register(), 5, null, null).andExpect(status().isForbidden());           // never bought

        String payer = register();
        long order = placeAndPay(payer, productId);                                     // paid, not delivered yet
        review(payer, 5, null, null).andExpect(status().isForbidden());
        shipAndDeliver(order);
        review(payer, 5, null, null).andExpect(status().isCreated());
    }

    @Test
    void oneReviewPerCustomerAndProduct() throws Exception {
        String c = buyerWhoReceived();
        review(c, 5, null, null).andExpect(status().isCreated());
        review(c, 1, null, null).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from reviews where product_id = ?", Integer.class, productId)).isEqualTo(1);
    }

    @Test
    void validationErrors() throws Exception {
        String c = buyerWhoReceived();
        review(c, 0, null, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.rating").exists());
        review(c, 6, null, null).andExpect(status().isBadRequest());
        review(c, 5, "x".repeat(101), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.title").exists());
        review(c, 5, null, "x".repeat(2001)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/" + productId + "/reviews?sort=random")).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/reviews"), c).content("{\"productId\":999999,\"rating\":5}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"rating\":5}")).andExpect(status().isUnauthorized());
    }

    @Test
    void ownerEditsAndDeletesOthersGet404() throws Exception {
        String owner = buyerWhoReceived();
        long id = json(review(owner, 2, "meh", null)).get("id").asLong();
        String other = register();

        mvc.perform(as(put("/api/reviews/" + id), other).content("{\"rating\":1}")).andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/reviews/" + id), other)).andExpect(status().isNotFound());

        mvc.perform(as(put("/api/reviews/" + id), owner).content("{\"rating\":4,\"title\":\"better now\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedAt").exists());
        assertThat(dbRating()).isEqualByComparingTo("4.0");

        mvc.perform(as(delete("/api/reviews/" + id), owner)).andExpect(status().isNoContent());
        assertThat(dbRating()).isEqualByComparingTo("0.0");
        assertThat(jdbc.queryForObject("select rating_count from products where id = ?", Integer.class, productId)).isZero();
    }

    @Test
    void adminHidesAReviewAndItLeavesTheAverage() throws Exception {
        review(buyerWhoReceived(), 5, null, null).andExpect(status().isCreated());
        String spammer = buyerWhoReceived();
        long spam = json(review(spammer, 1, "BUY CHEAP WATCHES", "spam link")).get("id").asLong();
        assertThat(dbRating()).isEqualByComparingTo("3.0");

        mvc.perform(as(post("/api/admin/reviews/" + spam + "/hide"), spammer)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/admin/reviews/" + spam + "/hide"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HIDDEN"));

        mvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.reviews.length()").value(1));
        assertThat(dbRating()).isEqualByComparingTo("5.0");

        mvc.perform(as(get("/api/reviews/mine"), spammer))                              // author still sees it
                .andExpect(jsonPath("$[0].status").value("HIDDEN"));

        mvc.perform(as(post("/api/admin/reviews/" + spam + "/publish"), admin)).andExpect(status().isOk());
        assertThat(dbRating()).isEqualByComparingTo("3.0");
    }

    @Test
    void scriptInAReviewIsStoredAsPlainText() throws Exception {
        String xss = "<script>alert(1)</script>";
        review(buyerWhoReceived(), 4, xss, xss).andExpect(status().isCreated());
        mvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.reviews[0].title").value(xss));      // returned as data; UI must escape
    }

    // ---------- wishlist ----------

    @Test
    void wishlistAddIsIdempotentAndShowsPriceDrop() throws Exception {
        String c = register();
        mvc.perform(as(post("/api/wishlist"), c).content("{\"productId\":" + productId + "}")).andExpect(status().isCreated());
        mvc.perform(as(post("/api/wishlist"), c).content("{\"productId\":" + productId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));

        jdbc.update("update products set price = 15.50 where id = ?", productId);
        mvc.perform(as(get("/api/wishlist"), c))
                .andExpect(jsonPath("$.items[0].priceWhenAdded").value(20.00))
                .andExpect(jsonPath("$.items[0].currentPrice").value(15.50))
                .andExpect(jsonPath("$.items[0].priceDrop").value(4.50))
                .andExpect(jsonPath("$.items[0].available").value(true));

        mvc.perform(as(delete("/api/wishlist/" + productId), c)).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        mvc.perform(as(delete("/api/wishlist/" + productId), c)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/wishlist"), c).content("{\"productId\":999999}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/wishlist")).andExpect(status().isUnauthorized());
    }

    @Test
    void moveToCartMovesOrKeepsTheItemWhenOutOfStock() throws Exception {
        String c = register();
        long soldOut = newProduct("9.99", 0);
        for (long id : new long[]{productId, soldOut}) {
            mvc.perform(as(post("/api/wishlist"), c).content("{\"productId\":" + id + "}")).andExpect(status().isCreated());
        }

        mvc.perform(as(post("/api/wishlist/" + productId + "/move-to-cart"), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(productId))
                .andExpect(jsonPath("$.items[0].quantity").value(1));

        mvc.perform(as(post("/api/wishlist/" + soldOut + "/move-to-cart"), c)).andExpect(status().isConflict());

        mvc.perform(as(get("/api/wishlist"), c))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(soldOut))
                .andExpect(jsonPath("$.items[0].available").value(false));
    }

    @Test
    void customersCannotSeeEachOthersWishlist() throws Exception {
        String a = register();
        String b = register();
        mvc.perform(as(post("/api/wishlist"), a).content("{\"productId\":" + productId + "}")).andExpect(status().isCreated());
        mvc.perform(as(get("/api/wishlist"), b)).andExpect(jsonPath("$.count").value(0));
        mvc.perform(as(delete("/api/wishlist/" + productId), b)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/wishlist"), a)).andExpect(jsonPath("$.count").value(1));
    }

    // ---------- helpers ----------

    private ResultActions review(String token, int rating, String title, String body) throws Exception {
        String json = mapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
            put("productId", productId);
            put("rating", rating);
            if (title != null) put("title", title);
            if (body != null) put("body", body);
        }});
        return mvc.perform(as(post("/api/reviews"), token).content(json));
    }

    private String buyerWhoReceived() throws Exception {
        String c = register();
        shipAndDeliver(placeAndPay(c, productId));
        return c;
    }

    private long placeAndPay(String token, long product) throws Exception {
        mvc.perform(as(post("/api/cart/items"), token).content("{\"productId\":" + product + ",\"quantity\":1}"))
                .andExpect(status().isCreated());
        long id = json(mvc.perform(as(post("/api/orders"), token).content(com.rbctcsworld.ecommerce.TestAddresses.ORDER_BODY)).andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(as(post("/api/orders/" + id + "/pay"), token)
                        .content("{\"cardNumber\":\"4242424242424242\",\"expiryMonth\":12,\"expiryYear\":2035,\"cvv\":\"123\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private void shipAndDeliver(long orderId) throws Exception {
        mvc.perform(as(post("/api/admin/orders/" + orderId + "/ship"), admin).content("{\"carrier\":\"UPS\"}")).andExpect(status().isOk());
        mvc.perform(as(post("/api/admin/orders/" + orderId + "/deliver"), admin)).andExpect(status().isOk());
    }

    private String register() throws Exception {
        return token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rv-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}")));
    }

    private long newProduct(String price, int stock) throws Exception {
        return json(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Reviewed\",\"sku\":\"RV-"
                + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":" + price + ",\"stock\":" + stock + "}"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private BigDecimal dbRating() {
        return jdbc.queryForObject("select rating_average from products where id = ?", BigDecimal.class, productId);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String token(ResultActions r) throws Exception {
        return json(r).get("token").asString();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
