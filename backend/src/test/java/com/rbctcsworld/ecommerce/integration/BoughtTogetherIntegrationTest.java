package com.rbctcsworld.ecommerce.integration;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Customers who bought this also bought" from real orders: REQ-CAT-13. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoughtTogetherIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String admin;

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String token(String path, String body) throws Exception {
        return json(mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))).get("token").asString();
    }

    private long product(String name, int stock) throws Exception {
        if (admin == null) admin = token("/api/auth/login", "{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}");
        return json(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"" + name + "\",\"sku\":\"BT-"
                + UUID.randomUUID().toString().substring(0, 10) + "\",\"price\":5.00,\"stock\":" + stock + "}"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    /** A new customer orders these products in ONE order. Returns the order id and the customer's token. */
    private long[] buy(String[] tokenOut, long... products) throws Exception {
        String t = token("/api/auth/register", "{\"email\":\"bt-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}");
        tokenOut[0] = t;
        for (long p : products) {
            mvc.perform(as(post("/api/cart/items"), t).content("{\"productId\":" + p + ",\"quantity\":1}")).andExpect(status().isCreated());
        }
        long id = json(mvc.perform(as(post("/api/orders"), t).content("{\"shippingAddress\":{\"fullName\":\"Ada Lovelace\","
                + "\"line1\":\"1 Main St\",\"city\":\"Pittsburgh\",\"state\":\"PA\",\"zip\":\"15213\"}}")).andExpect(status().isCreated())).get("id").asLong();
        return new long[]{id};
    }

    @Test
    void theMostOftenSharedProductsComeFirstAndCancelledSoldOutAndInactiveOnesAreLeftOut() throws Exception {
        long a = product("BT main", 50), b = product("BT often", 50), c = product("BT sometimes", 50);
        long cancelled = product("BT cancelled", 50), last = product("BT last one", 1);
        String[] t = new String[1];
        buy(t, a, b);
        buy(t, a, b, c);
        buy(t, a, c, last);                                        // the last unit of "last" goes in this order: sold out now
        long gone = buy(t, a, cancelled)[0];
        mvc.perform(as(post("/api/orders/" + gone + "/cancel"), t[0])).andExpect(status().isOk());
        buy(t, b, c);                                              // does not contain "a": never a source for it

        mvc.perform(get("/api/products/" + a + "/bought-together"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(b))           // 2 orders, "c" has 2 as well: ties go to the lower id
                .andExpect(jsonPath("$[1].id").value(c));
        mvc.perform(get("/api/products/" + a + "/bought-together?limit=1")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/products/" + b + "/bought-together")).andExpect(jsonPath("$[0].id").value(a));   // the other direction
    }

    @Test
    void anItemWithoutHistoryHasNoRecommendationsAndBadRequestsAreRefused() throws Exception {
        long fresh = product("BT nobody bought", 5);
        mvc.perform(get("/api/products/" + fresh + "/bought-together")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/products/" + fresh + "/bought-together?limit=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/" + fresh + "/bought-together?limit=13")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/999999999/bought-together")).andExpect(status().isNotFound());
    }
}
