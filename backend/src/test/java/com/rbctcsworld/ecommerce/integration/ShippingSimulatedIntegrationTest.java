package com.rbctcsworld.ecommerce.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The default setup (simulated shipping, no Shippo): no labels to print, and Shippo webhooks are refused. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShippingSimulatedIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void thereIsNoLabelToPrint() throws Exception {
        String admin = mapper.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}")).andReturn().getResponse()
                .getContentAsString()).get("token").asString();
        mvc.perform(get("/api/admin/orders/99999999/label").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void shippoWebhooksAreRefusedUntilATokenIsConfigured() throws Exception {
        String event = "{\"event\":\"track_updated\",\"data\":{\"tracking_number\":\"1\"}}";
        mvc.perform(post("/api/shipping/shippo/webhook").contentType(MediaType.APPLICATION_JSON).content(event))
                .andExpect(status().isBadRequest());                               // no token at all
        mvc.perform(post("/api/shipping/shippo/webhook?token=guess").contentType(MediaType.APPLICATION_JSON).content(event))
                .andExpect(status().isServiceUnavailable())                        // nothing to check it against
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.message").value("Shippo webhooks are not configured"));
    }
}
