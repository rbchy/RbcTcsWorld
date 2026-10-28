package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.notification.EmailSuppressions;
import com.rbctcsworld.ecommerce.notification.Notification;
import com.rbctcsworld.ecommerce.notification.NotificationDeliveryService;
import com.rbctcsworld.ecommerce.notification.NotificationRepository;
import com.rbctcsworld.ecommerce.notification.SnsHttp;
import com.rbctcsworld.ecommerce.notification.SnsTestSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SES bounce and complaint reports through the API and the database: a signed SNS report puts the address on the
 * suppression list, and the outbox stops e-mailing it. SNS's certificate download is replaced by the test certificate.
 */
@SpringBootTest(properties = "app.notifications.ses.sns-topic-arn=" + SnsTestSigner.TOPIC)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SesEventsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmailSuppressions suppressions;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationDeliveryService delivery;
    @MockitoBean SnsHttp sns;

    @BeforeEach
    void cleanList() {
        when(sns.apply(any())).thenAnswer(i -> SnsTestSigner.download(i.getArgument(0)));
        jdbc.update("DELETE FROM email_suppressions");
    }

    private org.springframework.test.web.servlet.ResultActions report(String body) throws Exception {
        // SNS posts JSON as text/plain
        return mvc.perform(post("/api/notifications/ses/events").contentType(MediaType.TEXT_PLAIN).content(body));
    }

    @Test
    void aBouncedAddressIsNeverEmailedAgain() throws Exception {
        report(SnsTestSigner.fixture("sns_bounce.json")).andExpect(status().isOk()).andExpect(jsonPath("$.received").value(true));
        assertThat(jdbc.queryForObject("SELECT reason FROM email_suppressions WHERE email = 'gone@example.com'", String.class))
                .isEqualTo("BOUNCE");
        report(SnsTestSigner.fixture("sns_bounce.json")).andExpect(status().isOk());           // SNS resends: still one row
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_suppressions", Integer.class)).isEqualTo(1);

        Notification n = notifications.save(new Notification(null, "GONE@example.com", "ORDER_PLACED", "Order received",
                "Thanks", LocalDateTime.now().minusMinutes(1)));
        delivery.sendDue();
        Notification after = notifications.findById(n.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(Notification.FAILED);
        assertThat(after.getLastError()).isEqualTo("Not sent: the address is on the suppression list after a bounce");
    }

    @Test
    void aComplaintIsKeptWithItsReason() throws Exception {
        report(SnsTestSigner.fixture("sns_complaint_v1.json")).andExpect(status().isOk());
        assertThat(suppressions.reason("Angry@Example.com")).isEqualTo(EmailSuppressions.COMPLAINT);
        assertThat(suppressions.reason("someone-else@example.com")).isNull();
    }

    @Test
    void aForgedOrForeignReportIs400AndChangesNothing() throws Exception {
        report(SnsTestSigner.fixture("sns_bounce.json").replace("Gone@Example.com", "victim@example.com"))
                .andExpect(status().isBadRequest());
        report(SnsTestSigner.fixture("sns_bounce.json").replace("rbctcsworld-ses-events", "someone-elses-topic"))
                .andExpect(status().isBadRequest());
        report("not json").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_suppressions", Integer.class)).isZero();
    }
}
