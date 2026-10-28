package com.rbctcsworld.ecommerce.fulfillment;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Who may tell the shop about a parcel (the secret token), and what is read from the call. */
class ShippoWebhookControllerTest {

    private static final String EVENT = "{\"event\":\"track_updated\",\"test\":true,\"data\":{\"carrier\":\"usps\","
            + "\"tracking_number\":\"9400111899223197428490\",\"tracking_status\":{\"status\":\"DELIVERED\"}}}";
    private final FulfillmentService fulfillment = mock(FulfillmentService.class);

    @Test
    void theRightTokenPassesTheParcelOn() {
        assertThat(new ShippoWebhookController(fulfillment, "s3cret").webhook("s3cret", EVENT)).isEqualTo(Map.of("received", true));
        verify(fulfillment).onTrackingUpdate("9400111899223197428490");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "wrong", "s3cret ", "S3CRET"})
    void noTokenOrAnotherTokenIs400(String token) {
        ShippoWebhookController c = new ShippoWebhookController(fulfillment, "s3cret");
        assertThatThrownBy(() -> c.webhook(token, EVENT)).isInstanceOf(BusinessRuleException.class)
                .hasMessage(ShippoWebhookController.INVALID);
        verifyNoInteractions(fulfillment);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void withoutAConfiguredSecretEveryCallIs503(String secret) {
        ShippoWebhookController c = new ShippoWebhookController(fulfillment, secret);
        assertThatThrownBy(() -> c.webhook("anything", EVENT)).isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> c.webhook(null, EVENT)).isInstanceOf(BusinessRuleException.class);   // no token: 400 first
        verifyNoInteractions(fulfillment);
    }

    @Test
    void otherEventsAndEmptyBodiesAreAcknowledgedAndIgnored() {
        ShippoWebhookController c = new ShippoWebhookController(fulfillment, "s3cret");
        assertThat(c.webhook("s3cret", null)).isEqualTo(Map.of("received", true));
        assertThat(c.webhook("s3cret", "{\"event\":\"transaction_created\",\"data\":{\"tracking_number\":\"1\"}}"))
                .isEqualTo(Map.of("received", true));
        c.webhook("s3cret", "{\"event\":\"track_updated\",\"data\":{\"tracking_number\":\" \"}}");
        c.webhook("s3cret", "{\"event\":\"track_updated\",\"data\":{}}");
        c.webhook("s3cret", "{\"event\":\"track_updated\"}");
        verifyNoInteractions(fulfillment);
    }

    @Test
    void aBodyThatIsNotJsonIs400() {
        assertThatThrownBy(() -> ShippoWebhookController.trackingNumber("not json")).isInstanceOf(BusinessRuleException.class)
                .hasMessage("Not a Shippo event");
    }
}
