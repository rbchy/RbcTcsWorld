package com.rbctcsworld.ecommerce.notification;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SES reports through SNS: who may send them, and which ones put an address on the suppression list. */
class SesEventControllerTest {

    private final EmailSuppressions suppressions = mock(EmailSuppressions.class);
    private final SnsHttp http = mock(SnsHttp.class);
    private SesEventController controller;

    @BeforeEach
    void setUp() {
        when(http.apply(any())).thenAnswer(i -> {
            URI u = i.getArgument(0);
            return u.getPath().endsWith(".pem") ? SnsTestSigner.download(u) : new byte[0];
        });
        when(suppressions.suppress(anyString(), anyString(), any())).thenReturn(true);
        controller = new SesEventController(suppressions, http, SnsTestSigner.TOPIC);
    }

    @Test
    void aPermanentBouncePutsTheAddressOnTheList() {
        assertThat(controller.event(SnsTestSigner.fixture("sns_bounce.json"))).isEqualTo(Map.of("received", true));
        verify(suppressions).suppress("Gone@Example.com", EmailSuppressions.BOUNCE, "smtp; 550 5.1.1 user unknown");
    }

    @Test
    void aComplaintPutsTheAddressOnTheList() {
        controller.event(SnsTestSigner.fixture("sns_complaint_v1.json"));
        verify(suppressions).suppress("angry@example.com", EmailSuppressions.COMPLAINT, "abuse");
    }

    @Test
    void theSubscriptionIsConfirmedByOpeningSnsSubscribeLink() {
        controller.event(SnsTestSigner.fixture("sns_subscription.json"));
        verify(http).apply(URI.create("https://sns.us-east-1.amazonaws.com/?Action=ConfirmSubscription&TopicArn="
                + SnsTestSigner.TOPIC + "&Token=token-123"));
        verifyNoInteractions(suppressions);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"notificationType\":\"Bounce\",\"bounce\":{\"bounceType\":\"Transient\",\"bouncedRecipients\":[{\"emailAddress\":\"full@example.com\"}]}}",
            "{\"notificationType\":\"Bounce\",\"bounce\":{\"bouncedRecipients\":[{\"emailAddress\":\"x@example.com\"}]}}",
            "{\"notificationType\":\"Delivery\",\"delivery\":{\"recipients\":[\"ok@example.com\"]}}",
            "{\"eventType\":\"Open\"}",
            "{\"notificationType\":\"Bounce\",\"bounce\":{\"bounceType\":\"Permanent\",\"bouncedRecipients\":[{\"emailAddress\":\" \"},{}]}}",
            "{\"notificationType\":\"Complaint\",\"complaint\":{}}"})
    void anythingElseChangesNothing(String sesEvent) {
        controller.event(SnsTestSigner.notification("m-1", sesEvent));
        verify(suppressions, never()).suppress(anyString(), anyString(), any());
    }

    @Test
    void aRepeatedReportIsAcknowledged() {
        when(suppressions.suppress(anyString(), anyString(), any())).thenReturn(false);
        assertThat(controller.event(SnsTestSigner.fixture("sns_bounce.json"))).isEqualTo(Map.of("received", true));
    }

    @Test
    void anotherTopicIsRefusedBeforeAnythingElse() {
        SesEventController other = new SesEventController(suppressions, http, "arn:aws:sns:us-east-1:123456789012:another-topic");
        assertThatThrownBy(() -> other.event(SnsTestSigner.fixture("sns_bounce.json"))).isInstanceOf(BusinessRuleException.class)
                .hasMessage(SesEventController.NOT_SNS);
        verify(http, never()).apply(any());
        verifyNoInteractions(suppressions);
    }

    @Test
    void aForgedReportIsRefused() {
        String forged = SnsTestSigner.fixture("sns_bounce.json").replace("Gone@Example.com", "victim@example.com");
        assertThatThrownBy(() -> controller.event(forged)).isInstanceOf(BusinessRuleException.class)
                .hasMessage(SnsMessageVerifier.INVALID);
        verifyNoInteractions(suppressions);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"not json", "[1,2]", "\"text\""})
    void aBodyThatIsNotAnSnsMessageIs400(String body) {
        assertThatThrownBy(() -> controller.event(body)).isInstanceOf(BusinessRuleException.class)
                .hasMessage(SesEventController.NOT_SNS);
    }

    @Test
    void aNotificationWhoseMessageIsNotJsonIs400() {
        assertThatThrownBy(() -> controller.event(SnsTestSigner.notification("m-2", "plain text")))
                .isInstanceOf(BusinessRuleException.class).hasMessage(SesEventController.NOT_SNS);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void withoutAConfiguredTopicEveryReportIs503(String topic) {
        SesEventController off = new SesEventController(suppressions, http, topic);
        assertThatThrownBy(() -> off.event(SnsTestSigner.fixture("sns_bounce.json"))).isInstanceOf(ServiceUnavailableException.class);
    }
}
