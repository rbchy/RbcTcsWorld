package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.notification.EmailSuppressions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin's list of blocked e-mail addresses, and allowing one again: REQ-NTF-07 (ADR-0014). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailSuppressionAdminIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EmailSuppressions suppressions;

    private String admin;
    private String tag;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}", "/api/auth/login");
        tag = UUID.randomUUID().toString().substring(0, 8);
    }

    private String token(String json, String path) throws Exception {
        return mapper.readTree(mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private JsonNode list(String search) throws Exception {
        var request = get("/api/admin/email-suppressions").header("Authorization", "Bearer " + admin);
        if (search != null) request.param("search", search);
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void theAdminSeesBlockedAddressesWithTheReasonAndCanSearchThem() throws Exception {
        suppressions.suppress("Gone-" + tag + "@Example.com", EmailSuppressions.BOUNCE, "smtp; 550 5.1.1 user unknown");
        suppressions.suppress("angry-" + tag + "@example.com", EmailSuppressions.COMPLAINT, "abuse");

        JsonNode found = list(tag);
        assertThat(found).hasSize(2);
        JsonNode gone = null;
        for (JsonNode row : found) if (row.get("email").asString().startsWith("gone-")) gone = row;
        assertThat(gone).isNotNull();
        assertThat(gone.get("email").asString()).isEqualTo("gone-" + tag + "@example.com");      // stored in lower case
        assertThat(gone.get("reason").asString()).isEqualTo("BOUNCE");
        assertThat(gone.get("detail").asString()).contains("550");
        assertThat(gone.get("createdAt").isNull()).isFalse();

        assertThat(list("angry-" + tag)).hasSize(1);
        assertThat(list("%")).isNotNull();                                                        // a wildcard is just text
    }

    @Test
    void allowingAnAddressAgainRemovesItSoTheOutboxSendsToItAgain() throws Exception {
        String email = "fixed-" + tag + "@example.com";
        suppressions.suppress(email, EmailSuppressions.BOUNCE, "mailbox full");
        assertThat(suppressions.reason(email)).isEqualTo("BOUNCE");

        mvc.perform(delete("/api/admin/email-suppressions").param("email", " FIXED-" + tag + "@example.com ")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(suppressions.reason(email)).isNull();
        assertThat(list(tag)).isEmpty();

        mvc.perform(delete("/api/admin/email-suppressions").param("email", email).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/email-suppressions").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAdminSeesOrChangesTheList() throws Exception {
        String customer = token("{\"email\":\"sup-" + tag + "@test.com\",\"password\":\"Password1!\"}", "/api/auth/register");
        mvc.perform(get("/api/admin/email-suppressions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/email-suppressions").header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/email-suppressions").param("email", "x@y.z").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
    }
}
