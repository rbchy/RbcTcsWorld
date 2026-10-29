package com.rbctcsworld.ecommerce.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Affiliate products through the real API and database: REQ-AFF-01..02 (ADR-0020). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AffiliateIntegrationTest {

    private static final String LINK = "https://www.amazon.com/dp/B0TESTLINK?tag=rbctcsworld-20";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String admin;
    private String customer;

    @BeforeEach
    void setUp() throws Exception {
        admin = token(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"));
        customer = token(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"aff-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"));
    }

    private String token(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private static String body(String sku, String link) {
        return "{\"name\":\"Running shoe " + sku + "\",\"sku\":\"" + sku + "\",\"category\":\"shoes\",\"price\":59.99,\"stock\":5"
                + (link == null ? "" : ",\"affiliateUrl\":\"" + link + "\"") + "}";
    }

    private ResultActions save(String method, String path, String json) throws Exception {
        var request = (method.equals("POST") ? post(path) : put(path))
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(request);
    }

    private long create(String sku, String link) throws Exception {
        return mapper.readTree(save("POST", "/api/products", body(sku, link)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions addToCart(long productId) throws Exception {
        return mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":" + productId + ",\"quantity\":1}"));
    }

    private static String sku() {
        return "AFF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    void theAdminAddsAPartnerProductAndCustomersSeeItsLink() throws Exception {
        long id = create(sku(), LINK);
        mvc.perform(get("/api/products/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliateUrl").value(LINK))
                .andExpect(jsonPath("$.soldByPartner").doesNotExist());
        long ordinary = create(sku(), null);
        mvc.perform(get("/api/products/" + ordinary)).andExpect(jsonPath("$.affiliateUrl").doesNotExist());
    }

    @Test
    void aPartnerProductCannotGoIntoTheCart() throws Exception {
        long id = create(sku(), LINK);
        addToCart(id).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("sold by our partner shop")));
    }

    @Test
    void aProductThatBecomesAPartnerProductInTheCartCannotBeOrdered() throws Exception {
        String sku = sku();
        long id = create(sku, null);
        addToCart(id).andExpect(status().isCreated());
        save("PUT", "/api/products/" + id, body(sku, LINK)).andExpect(status().isOk());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$.items[0].available").value(false));
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddress\":{\"fullName\":\"Ada Lovelace\",\"line1\":\"12 Main St\",\"city\":\"Pittsburgh\",\"state\":\"PA\",\"zip\":\"15213\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("remove it from the cart")));

        // the link removed again (PUT replaces): sold here again, the order goes through
        save("PUT", "/api/products/" + id, body(sku, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliateUrl").doesNotExist());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$.items[0].available").value(true));
    }

    @Test
    void onlyHttpsLinksAreAccepted() throws Exception {
        for (String bad : new String[]{"http://www.amazon.com/dp/B0", "javascript:alert(1)", "https://a b.com", "www.amazon.com"}) {
            save("POST", "/api/products", body(sku(), bad)).andExpect(status().isBadRequest());
        }
    }
}
