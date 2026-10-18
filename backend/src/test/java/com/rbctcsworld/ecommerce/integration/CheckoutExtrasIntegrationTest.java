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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Guest checkout and the address book through the real API and database: REQ-AUTH-15, REQ-ADR-01..03. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutExtrasIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private static String address(String line1, String zip) {
        return "{\"fullName\":\"Ada Lovelace\",\"line1\":\"" + line1 + "\",\"city\":\"Pittsburgh\",\"state\":\"pa\",\"zip\":\"" + zip + "\"}";
    }

    private String customer() throws Exception {
        return json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ab-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))).get("token").asString();
    }

    private long product() throws Exception {
        String admin = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"))).get("token").asString();
        return json(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Guest lamp\",\"sku\":\"GL-"
                + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":12.00,\"stock\":5}"))).get("id").asLong();
    }

    // ------------------------------------------------------------------ guest checkout

    @Test
    void aGuestShopsPaysAndLaterClaimsTheAccountWithForgotPassword() throws Exception {
        String email = "guest-" + UUID.randomUUID() + "@test.com";
        JsonNode session = json(mvc.perform(post("/api/auth/guest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guest").value(true))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.email").value(email)));
        String token = session.get("token").asString();

        long p = product();
        mvc.perform(as(post("/api/cart/items"), token).content("{\"productId\":" + p + ",\"quantity\":1}")).andExpect(status().isCreated());
        long order = json(mvc.perform(as(post("/api/orders"), token).content("{\"shippingAddress\":" + address("1 Main St", "15213") + "}"))
                .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(as(post("/api/orders/" + order + "/pay"), token)
                        .content("{\"cardNumber\":\"4242424242424242\",\"expiryMonth\":12,\"expiryYear\":2030,\"cvv\":\"123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));

        // the same e-mail again: told to set a password, nobody else can open these orders
        mvc.perform(post("/api/auth/guest").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Forgot password")));
        // the guest's random password is unknown: logging in is impossible
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Password1!\"}")).andExpect(status().isUnauthorized());
        // the refresh token works like any other
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + session.get("refreshToken").asString() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.guest").value(true));
        assertThat(jdbc.queryForObject("select guest from users where email = ?", Boolean.class, email)).isTrue();
    }

    @Test
    void anEmailWithAnAccountMustLogInInstead() throws Exception {
        String email = "full-" + UUID.randomUUID() + "@test.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Password1!\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.guest").value(false));
        mvc.perform(post("/api/auth/guest").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("This e-mail has an account. Please log in."));
        mvc.perform(post("/api/auth/guest").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    // ------------------------------------------------------------------ address book

    @Test
    void theAddressBookKeepsOneDefaultRefusesTwinsAndIsPrivate() throws Exception {
        String ada = customer();
        long home = json(mvc.perform(as(post("/api/addresses"), ada).content("{\"label\":\"Home\",\"address\":" + address("1 Main St", "15213") + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.address.state").value("PA"))).get("id").asLong();
        long office = json(mvc.perform(as(post("/api/addresses"), ada).content("{\"label\":\"Office\",\"address\":" + address("9 Work Rd", "15222") + ",\"makeDefault\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isDefault").value(true))).get("id").asLong();

        mvc.perform(as(get("/api/addresses"), ada)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(office))
                .andExpect(jsonPath("$[1].isDefault").value(false));

        mvc.perform(as(post("/api/addresses"), ada).content("{\"address\":" + address("1 MAIN ST", "15213") + "}"))
                .andExpect(status().isConflict());
        mvc.perform(as(post("/api/addresses"), ada).content("{\"label\":\"<b>\",\"address\":" + address("", "123") + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['address.line1']").exists())
                .andExpect(jsonPath("$.fieldErrors['address.zip']").exists())
                .andExpect(jsonPath("$.fieldErrors.label").exists());

        String bob = customer();
        mvc.perform(as(put("/api/addresses/" + home), bob).content("{\"address\":" + address("2 Main St", "15213") + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/addresses/" + home), bob)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/addresses"), bob)).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(as(delete("/api/addresses/" + office), ada)).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/addresses"), ada)).andExpect(jsonPath("$[0].id").value(home)).andExpect(jsonPath("$[0].isDefault").value(true));
        mvc.perform(get("/api/addresses")).andExpect(status().isUnauthorized());
    }

    @Test
    void anOrderKeepsItsAddressWhenTheSavedOneChanges() throws Exception {
        String ada = customer();
        long saved = json(mvc.perform(as(post("/api/addresses"), ada).content("{\"address\":" + address("1 Main St", "15213") + "}"))).get("id").asLong();
        long p = product();
        mvc.perform(as(post("/api/cart/items"), ada).content("{\"productId\":" + p + ",\"quantity\":1}")).andExpect(status().isCreated());
        long order = json(mvc.perform(as(post("/api/orders"), ada).content("{\"shippingAddress\":" + address("1 Main St", "15213") + "}"))).get("id").asLong();

        mvc.perform(as(put("/api/addresses/" + saved), ada).content("{\"address\":" + address("77 New Ave", "15213") + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address.line1").value("77 New Ave"));
        mvc.perform(as(get("/api/orders/" + order), ada)).andExpect(jsonPath("$.shippingAddress.line1").value("1 Main St"));
    }
}
