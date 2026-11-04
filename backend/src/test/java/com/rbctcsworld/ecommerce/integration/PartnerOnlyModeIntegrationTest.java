package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.TestAddresses;
import com.rbctcsworld.ecommerce.store.SalesClosedInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STORE_SALES=off: the site runs as a partner (affiliate) site (REQ-AFF-04, ADR-0026). Visitors see only partner
 * products; every way to buy from the shop itself is 409; the admin still sees everything.
 */
@SpringBootTest(properties = "app.store.sales=off")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartnerOnlyModeIntegrationTest {

    private static final String LINK = "https://www.amazon.com/dp/B0PARTNER1?tag=rbctcsworld-20";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String admin;
    private String customer;
    private String tag;
    private long own;
    private long partner;

    @BeforeEach
    void setUp() throws Exception {
        admin = token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}")));
        customer = token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"partner-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated()));
        tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        own = product("{\"name\":\"Own lamp " + tag + "\",\"sku\":\"OWN-" + tag + "\",\"category\":\"home\",\"price\":20.00,\"stock\":5}");
        partner = product("{\"name\":\"Partner shoe " + tag + "\",\"sku\":\"PRT-" + tag + "\",\"category\":\"shoes\",\"price\":79.99,"
                + "\"stock\":3,\"affiliateUrl\":\"" + LINK + "\"}");
    }

    private long product(String json) throws Exception {
        return read(mvc.perform(as(post("/api/products"), admin).content(json)).andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode read(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String token(ResultActions r) throws Exception {
        return read(r).get("token").asString();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void theStorefrontIsToldItIsAPartnerSite() throws Exception {
        mvc.perform(get("/api/store/mode")).andExpect(status().isOk()).andExpect(jsonPath("$.sales").value(false));
    }

    @Test
    void visitorsAndCustomersSeeOnlyPartnerProductsTheAdminSeesAll() throws Exception {
        JsonNode visitor = read(mvc.perform(get("/api/products").param("q", tag).param("size", "50")).andExpect(status().isOk()));
        assertThat(visitor).hasSize(1);
        assertThat(visitor.get(0).get("id").asLong()).isEqualTo(partner);
        assertThat(read(mvc.perform(as(get("/api/products"), customer).param("q", tag)))).hasSize(1);
        assertThat(read(mvc.perform(as(get("/api/products"), admin).param("q", tag)))).hasSize(2);

        mvc.perform(get("/api/products/" + partner)).andExpect(status().isOk());
        mvc.perform(get("/api/products/" + own)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/" + own + "/similar")).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/products/" + own), admin)).andExpect(status().isOk());       // the admin edits it

        assertThat(read(mvc.perform(get("/api/products/categories"))).toString()).contains("shoes");
        JsonNode facets = read(mvc.perform(get("/api/products/facets").param("q", tag)).andExpect(status().isOk()));
        assertThat(facets.toString()).doesNotContain("\"home\"");
        mvc.perform(get("/api/seo/sitemap.xml")).andExpect(status().isOk())
                .andExpect(r -> assertThat(r.getResponse().getContentAsString())
                        .contains("/products/" + partner + "/").doesNotContain("/products/" + own + "/")
                        .doesNotContain("/returns-policy"));
    }

    @Test
    void everyWayToBuyFromTheShopItselfIsClosed() throws Exception {
        expectClosed(mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + own + ",\"quantity\":1}")));
        expectClosed(mvc.perform(as(get("/api/cart"), customer)));
        expectClosed(mvc.perform(as(post("/api/checkout/quote"), customer).content("{}")));
        expectClosed(mvc.perform(as(post("/api/orders"), customer).content(TestAddresses.ORDER_BODY)));
        expectClosed(mvc.perform(as(get("/api/orders"), customer)));
        expectClosed(mvc.perform(as(get("/api/payment-methods"), customer)));
        expectClosed(mvc.perform(as(get("/api/payments/config"), customer)));
        expectClosed(mvc.perform(post("/api/auth/guest").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"guest-" + tag + "@test.com\"}")));
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());               // the login rule still comes first
    }

    @Test
    void partnerProductsCanStillBeKeptOnTheWishlist() throws Exception {
        mvc.perform(as(post("/api/wishlist"), customer).content("{\"productId\":" + partner + "}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isIn(200, 201));
        expectClosed(mvc.perform(as(post("/api/wishlist/" + partner + "/move-to-cart"), customer)));
    }

    private static void expectClosed(ResultActions r) throws Exception {
        r.andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(SalesClosedInterceptor.CLOSED));
    }
}
