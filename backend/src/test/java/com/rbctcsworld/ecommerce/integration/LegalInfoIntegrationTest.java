package com.rbctcsworld.ecommerce.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The policy pages read their numbers from the same settings the shop uses, without a login. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegalInfoIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void anyoneCanReadTheStoresLegalDetailsAndTheyMatchTheShopsRules() throws Exception {
        mvc.perform(get("/api/store/legal")).andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").value("RbcTcsWorld Shop"))
                .andExpect(jsonPath("$.returnWindowDays").value(30))           // app.returns.window-days
                .andExpect(jsonPath("$.shippingFee").value(5.99))              // app.pricing.shipping-fee
                .andExpect(jsonPath("$.freeShippingThreshold").value(50.00))   // app.pricing.free-shipping-threshold
                .andExpect(jsonPath("$.governingLaw").value("Pennsylvania"))
                .andExpect(jsonPath("$.complete").value(false))                // no business registered yet
                .andExpect(jsonPath("$.reviewed").value(false));
    }
}
