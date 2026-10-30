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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Product descriptions through the real API and database: REQ-CAT-16 (ADR-0021). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductDescriptionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = mapper.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private ResultActions create(String description) throws Exception {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>(java.util.Map.of("name", "Trail shoe",
                "sku", "DSC-" + UUID.randomUUID().toString().substring(0, 8), "category", "shoes", "price", 59.99, "stock", 3));
        if (description != null) body.put("description", description);
        return mvc.perform(post("/api/products").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    @Test
    void theDescriptionIsStoredTidiedAndShownToEveryone() throws Exception {
        long id = mapper.readTree(create("  Light and grippy.\r\n\r\n\r\nGood for wet rocks.  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Light and grippy.\n\nGood for wet rocks."))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.description").value("Light and grippy.\n\nGood for wet rocks."));
    }

    @Test
    void aProductWithoutADescriptionHasNoSuchField() throws Exception {
        create(null).andExpect(status().isCreated()).andExpect(jsonPath("$.description").doesNotExist());
        create("   ").andExpect(status().isCreated()).andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void markupAndOverlongTextAreRefused() throws Exception {
        create("Nice <script>alert(1)</script>").andExpect(status().isBadRequest());
        create("x".repeat(4001)).andExpect(status().isBadRequest());
        create("x".repeat(4000)).andExpect(status().isCreated());
    }
}
