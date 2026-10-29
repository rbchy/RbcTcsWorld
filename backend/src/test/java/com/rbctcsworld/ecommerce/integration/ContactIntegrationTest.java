package com.rbctcsworld.ecommerce.integration;

import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The contact form through the real API, database and outbox: REQ-CS-01..03 (ADR-0018). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = token(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"));
    }

    private String token(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private static String unique() {
        return "cs-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    private ResultActions send(String bearer, String json) throws Exception {
        var request = post("/api/contact").contentType(MediaType.APPLICATION_JSON).content(json);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return mvc.perform(request);
    }

    private static String form(String email, String message) {
        return "{\"name\":\"Ada Lovelace\",\"email\":\"" + email + "\",\"topic\":\"RETURN\",\"orderNumber\":\"ord-77\",\"message\":\""
                + message + "\"}";
    }

    private int outbox(String recipient, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient = ? AND type = ?", Integer.class, recipient, type);
    }

    @Test
    void aVisitorWritesGetsATicketAndTheOwnerIsToldThroughTheOutbox() throws Exception {
        mvc.perform(get("/api/contact")).andExpect(status().isOk())
                .andExpect(jsonPath("$.replyDays").value(2)).andExpect(jsonPath("$.topics[0]").value("ORDER"));
        String email = unique();
        JsonNode sent = mapper.readTree(send(null, form(email, "Can I return a mug I opened?"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticket").value(matchesPattern("MSG-[A-Z2-9]{6}")))
                .andReturn().getResponse().getContentAsString());
        String ticket = sent.get("ticket").asString();

        assertThat(jdbc.queryForObject("SELECT status FROM contact_messages WHERE ticket = ?", String.class, ticket)).isEqualTo("NEW");
        assertThat(jdbc.queryForObject("SELECT order_number FROM contact_messages WHERE ticket = ?", String.class, ticket)).isEqualTo("ORD-77");
        assertThat(outbox(email, "CONTACT_RECEIVED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT body FROM notifications WHERE recipient = ? AND type = 'CONTACT_RECEIVED'",
                String.class, email)).contains(ticket).doesNotContain("mug I opened");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type = 'CONTACT_NEW' AND subject LIKE ?",
                Integer.class, "%" + ticket + "%")).isEqualTo(1);
    }

    @Test
    void aLoggedInCustomerAlwaysWritesWithTheirOwnAddress() throws Exception {
        String email = unique();
        String customer = token(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Password1!\"}"));
        String ticket = mapper.readTree(send(customer, form("victim@example.com", "Please cancel my order"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("ticket").asString();
        assertThat(jdbc.queryForObject("SELECT email FROM contact_messages WHERE ticket = ?", String.class, ticket)).isEqualTo(email);
        assertThat(jdbc.queryForObject("SELECT user_id FROM contact_messages WHERE ticket = ?", Long.class, ticket)).isNotNull();
        assertThat(outbox("victim@example.com", "CONTACT_RECEIVED")).isZero();
    }

    @Test
    void aBotThatFillsTheHiddenFieldGetsNothing() throws Exception {
        String email = unique();
        send(null, "{\"name\":\"Bot\",\"email\":\"" + email + "\",\"topic\":\"OTHER\",\"message\":\"cheap watches here\",\"website\":\"x\"}")
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contact_messages WHERE email = ?", Integer.class, email)).isZero();
        assertThat(outbox(email, "CONTACT_RECEIVED")).isZero();
    }

    @Test
    void theFourthMessageFromOneAddressWithinAnHourIs429() throws Exception {
        String email = unique();
        for (int i = 0; i < 3; i++) send(null, form(email, "Question number " + i + " here")).andExpect(status().isCreated());
        send(null, form(email.toUpperCase(), "Question number four here"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"));
    }

    @Test
    void badFormsAre400() throws Exception {
        send(null, "{\"name\":\"\",\"email\":\"a@b.co\",\"topic\":\"OTHER\",\"message\":\"long enough text\"}").andExpect(status().isBadRequest());
        send(null, "{\"name\":\"A\",\"email\":\"not-an-email\",\"topic\":\"OTHER\",\"message\":\"long enough text\"}").andExpect(status().isBadRequest());
        send(null, "{\"name\":\"A\",\"email\":\"a@b.co\",\"topic\":\"SPAM\",\"message\":\"long enough text\"}").andExpect(status().isBadRequest());
        send(null, "{\"name\":\"A\",\"email\":\"a@b.co\",\"topic\":\"OTHER\",\"message\":\"short\"}").andExpect(status().isBadRequest());
        send(null, "{\"name\":\"A\",\"topic\":\"OTHER\",\"message\":\"long enough text\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please give your e-mail address so we can answer"));
        send(null, "{\"name\":\"A\",\"email\":\"a@b.co\",\"topic\":\"OTHER\",\"orderNumber\":\"<script>\",\"message\":\"long enough text\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void theOwnerAnswersOnceThenClosesAndTheAnswerIsEmailed() throws Exception {
        String email = unique();
        String ticket = mapper.readTree(send(null, form(email, "Do you ship to Alaska?"))
                .andReturn().getResponse().getContentAsString()).get("ticket").asString();
        long id = jdbc.queryForObject("SELECT id FROM contact_messages WHERE ticket = ?", Long.class, ticket);

        JsonNode list = mapper.readTree(mvc.perform(get("/api/admin/messages?status=NEW").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        boolean listed = false;
        for (JsonNode m : list) listed |= m.get("ticket").asString().equals(ticket);
        assertThat(listed).isTrue();

        mvc.perform(post("/api/admin/messages/" + id + "/reply").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Yes, with USPS.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.answeredBy").value("admin@rbctcsworld.com"));
        assertThat(outbox(email, "CONTACT_REPLY")).isEqualTo(1);
        mvc.perform(post("/api/admin/messages/" + id + "/reply").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Again\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/messages/" + id + "/close").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(post("/api/admin/messages/99999999/close").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/messages?status=SPAM").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheOwnerReadsTheMessages() throws Exception {
        String customer = token(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + unique() + "\",\"password\":\"Password1!\"}"));
        mvc.perform(get("/api/admin/messages")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/messages").header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
    }
}
