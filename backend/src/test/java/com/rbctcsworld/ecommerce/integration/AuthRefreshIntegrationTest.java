package com.rbctcsworld.ecommerce.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Refresh tokens through the real HTTP layer and database: rotation, reuse detection, logout. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRefreshIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private JsonNode register() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@test.com";
        return json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(900)));
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}"));
    }

    private ResultActions logout(String token) throws Exception {
        return mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}"));
    }

    @Test
    void refreshGivesAWorkingAccessTokenAndANewRefreshToken() throws Exception {
        JsonNode first = register();
        JsonNode second = json(refresh(first.get("refreshToken").asString()).andExpect(status().isOk()));

        assertThat(second.get("refreshToken").asString()).isNotEqualTo(first.get("refreshToken").asString());
        assertThat(second.get("email").asString()).isEqualTo(first.get("email").asString());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + second.get("token").asString()))
                .andExpect(status().isOk());
    }

    @Test
    void reusingASpentTokenLogsOutTheWholeSession() throws Exception {
        String first = register().get("refreshToken").asString();
        String second = json(refresh(first).andExpect(status().isOk())).get("refreshToken").asString();
        String third = json(refresh(second).andExpect(status().isOk())).get("refreshToken").asString();

        refresh(first).andExpect(status().isUnauthorized())          // stolen copy comes back after its successor was used
                .andExpect(jsonPath("$.message").value("Session expired. Please log in again."));
        refresh(third).andExpect(status().isUnauthorized());         // family revoked: the real user logs in again
    }

    @Test
    void aLostResponseIsNotTheft() throws Exception {
        String first = register().get("refreshToken").asString();
        String lost = json(refresh(first).andExpect(status().isOk())).get("refreshToken").asString();   // never arrived

        String retry = json(refresh(first).andExpect(status().isOk())).get("refreshToken").asString();  // within 30 s
        assertThat(retry).isNotEqualTo(lost);
        refresh(retry).andExpect(status().isOk());                   // the session goes on
    }

    @Test
    void logoutMakesTheRefreshTokenWorthlessAndIsIdempotent() throws Exception {
        String token = register().get("refreshToken").asString();
        logout(token).andExpect(status().isNoContent());
        refresh(token).andExpect(status().isUnauthorized());
        logout(token).andExpect(status().isNoContent());
        logout("never-issued").andExpect(status().isNoContent());
    }

    @Test
    void onlyTheHashIsStored() throws Exception {
        String token = register().get("refreshToken").asString();
        Integer plain = jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, token);
        assertThat(plain).isZero();
    }

    @Test
    void unknownOrMissingTokens() throws Exception {
        refresh("not-a-real-token").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        refresh("x".repeat(201)).andExpect(status().isBadRequest());
    }

    @Test
    void loginAlsoReturnsARefreshToken() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@test.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Password1!\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(900));
    }
}
