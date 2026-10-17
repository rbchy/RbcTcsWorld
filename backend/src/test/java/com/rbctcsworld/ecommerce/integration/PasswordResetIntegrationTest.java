package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.notification.NotificationDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Forgot password" through the real API, outbox and database (H2). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTest {

    private static final String ACCEPTED = "If an account exists for this e-mail, we have sent a link to reset the password.";
    private static final String INVALID = "This reset link is invalid or has expired. Please ask for a new one.";
    private static final Pattern LINK = Pattern.compile("/#/reset-password\\?token=([A-Za-z0-9_-]+)");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationDeliveryService delivery;

    private static String email() {
        return "reset-" + UUID.randomUUID() + "@test.com";
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Registers and returns the refresh token of that first session. */
    private String register(String email) throws Exception {
        String body = postJson("/api/auth/register", "{\"email\":\"" + email + "\",\"password\":\"Password1!\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("refreshToken").asString();
    }

    private ResultActions forgot(String email) throws Exception {
        return postJson("/api/auth/forgot-password", "{\"email\":\"" + email + "\"}");
    }

    private ResultActions reset(String token, String password) throws Exception {
        return postJson("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}");
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private List<String> emailTypes(String email) {
        return jdbc.queryForList("select type from notifications where recipient = ? order by id", String.class, email);
    }

    /** The token from the newest reset e-mail in the outbox (before it is delivered and redacted). */
    private String latestLink(String email) {
        String body = jdbc.queryForObject("select body from notifications where recipient = ? and type = 'PASSWORD_RESET' "
                + "order by id desc limit 1", String.class, email);
        Matcher m = LINK.matcher(body);
        assertThat(m.find()).as(body).isTrue();
        return m.group(1);
    }

    private String resetEmailStatus(String email) {
        return jdbc.queryForObject("select status from notifications where recipient = ? and type = 'PASSWORD_RESET'",
                String.class, email);
    }

    @Test
    void knownAndUnknownEmailsGetTheSameAnswerButOnlyACustomerGetsAnEmail() throws Exception {
        String customer = email();
        register(customer);
        String stranger = email();

        String a = forgot(customer).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String b = forgot(stranger).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(a).isEqualTo(b).contains(ACCEPTED);
        assertThat(emailTypes(customer)).containsExactly("PASSWORD_RESET");
        assertThat(emailTypes(stranger)).isEmpty();
        forgot("not an e-mail").andExpect(status().isBadRequest());
    }

    @Test
    void aResetChangesThePasswordEndsEverySessionAndTheLinkWorksOnce() throws Exception {
        String email = email();
        String oldSession = register(email);
        forgot(email.toUpperCase()).andExpect(status().isAccepted());     // any case: e-mails are normalised
        String token = latestLink(email);
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens where token_hash = ?", Integer.class, token))
                .as("only the hash is stored").isZero();

        reset(token, "BrandNew1!").andExpect(status().isNoContent());

        login(email, "Password1!").andExpect(status().isUnauthorized());
        login(email, "BrandNew1!").andExpect(status().isOk());
        postJson("/api/auth/refresh", "{\"refreshToken\":\"" + oldSession + "\"}").andExpect(status().isUnauthorized());
        reset(token, "Another1!").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALID));
        assertThat(emailTypes(email)).containsExactly("PASSWORD_RESET", "PASSWORD_CHANGED");
    }

    @Test
    void aNewerLinkMakesTheOlderOneUseless() throws Exception {
        String email = email();
        register(email);
        forgot(email);
        String first = latestLink(email);
        forgot(email);
        String second = latestLink(email);

        reset(first, "BrandNew1!").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALID));
        reset(second, "BrandNew1!").andExpect(status().isNoContent());
    }

    @Test
    void anExpiredOrUnknownLinkIsRefused() throws Exception {
        String email = email();
        register(email);
        forgot(email);
        String token = latestLink(email);
        jdbc.update("update password_reset_tokens set expires_at = created_at "
                + "where user_id = (select id from users where email = ?)", email);

        reset(token, "BrandNew1!").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALID));
        reset("made-up-token", "BrandNew1!").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALID));
        login(email, "Password1!").andExpect(status().isOk());
    }

    @Test
    void aWeakPasswordIsRejectedAndTheLinkStillWorksAfterwards() throws Exception {
        String email = email();
        register(email);
        forgot(email);
        String token = latestLink(email);

        reset(token, "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        reset(token, "LongEnough1!").andExpect(status().isNoContent());
    }

    @Test
    void atMostThreeLinksPerHour() throws Exception {
        String email = email();
        register(email);
        for (int i = 0; i < 5; i++) {
            forgot(email).andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(ACCEPTED));
        }
        assertThat(emailTypes(email)).containsExactly("PASSWORD_RESET", "PASSWORD_RESET", "PASSWORD_RESET");
    }

    @Test
    void aResetLiftsALoginLockout() throws Exception {
        String email = email();
        register(email);
        for (int i = 0; i < 5; i++) login(email, "Wrong-password1").andExpect(status().isUnauthorized());
        login(email, "Password1!").andExpect(status().isTooManyRequests());

        forgot(email);
        reset(latestLink(email), "BrandNew1!").andExpect(status().isNoContent());

        login(email, "BrandNew1!").andExpect(status().isOk());
    }

    @Test
    void theDeliveredResetEmailNoLongerContainsTheLink() throws Exception {
        String email = email();
        register(email);
        forgot(email);
        String token = latestLink(email);

        // other tests leave e-mails in the shared outbox too: deliver in rounds until this one went out
        for (int round = 0; round < 50 && !"SENT".equals(resetEmailStatus(email)); round++) {
            delivery.sendDue();
        }
        assertThat(resetEmailStatus(email)).isEqualTo("SENT");

        String stored = jdbc.queryForObject("select body from notifications where recipient = ? and type = 'PASSWORD_RESET'",
                String.class, email);
        assertThat(stored).doesNotContain(token).isEqualTo("[password reset link removed after delivery]");
        reset(token, "BrandNew1!").andExpect(status().isNoContent());           // the e-mailed link itself still works
    }
}
