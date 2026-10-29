package com.rbctcsworld.ecommerce.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public demo (DEMO_MODE=true) as deployed by deploy/docker-compose.yml: own secret and admin password
 * (the development ones would stop the start), and product pictures only from the shop's own files.
 * Own H2 database, so the admin is seeded with the demo password.
 */
@SpringBootTest(properties = {
        "app.demo.enabled=true",
        "app.jwt.secret=demo-integration-test-secret-that-is-long-enough-0123456789",
        "app.seed.admin-email=demo-admin@rbctcsworld.test",
        "app.seed.admin-password=Demo-Admin-Pass-2026!",
        "spring.datasource.url=jdbc:h2:mem:demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoModeIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String adminToken() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo-admin@rbctcsworld.test\",\"password\":\"Demo-Admin-Pass-2026!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asString();
    }

    private String product(String imageUrl) {
        return "{\"name\":\"Demo pic\",\"sku\":\"DEMO-" + UUID.randomUUID().toString().substring(0, 8)
                + "\",\"price\":1.00,\"stock\":1,\"imageUrl\":\"" + imageUrl + "\"}";
    }

    @Test
    void outsideLinkIsRefusedWithAClearMessage() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(product("https://cdn.example.com/a.png")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("own pictures")));
    }

    @Test
    void theShopsOwnPictureIsAccepted() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(product("/products/home-mug-002.svg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("/products/home-mug-002.svg"));
    }

    /** REQ-CAT-15: anybody can be the admin of the demo, so photo uploads are switched off there (ADR-0017). */
    @Test
    void photoUploadsAreSwitchedOffInTheDemo() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/products/pictures")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3}))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not take photo uploads")));
    }

    @Test
    void developmentAdminPasswordDoesNotExistHere() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isUnauthorized());
    }
}
