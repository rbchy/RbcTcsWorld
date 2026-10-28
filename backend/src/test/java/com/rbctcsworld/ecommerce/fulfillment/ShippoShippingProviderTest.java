package com.rbctcsworld.ecommerce.fulfillment;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ServiceUnavailableException;
import com.rbctcsworld.ecommerce.fulfillment.ShippingProvider.Address;
import com.rbctcsworld.ecommerce.fulfillment.ShippingProvider.Label;
import com.rbctcsworld.ecommerce.fulfillment.ShippingProvider.LabelRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Which label is bought (the chosen carrier's cheapest), for which parcel, and what happens when none can be. */
class ShippoShippingProviderTest {

    private static final Address FROM = new Address("Shop", "965 Mission St", null, "San Francisco", "CA", "94103", "4155550100");
    private static final Address TO = new Address("Ada", "12 Main St", null, "Pittsburgh", "PA", "15213", null);
    private static final ShippoClient.Parcel BOX = new ShippoClient.Parcel("10", "8", "4", null);
    private static final List<ShippoClient.Rate> RATES = List.of(
            new ShippoClient.Rate("r_usps_priority", "USPS", "Priority Mail", new BigDecimal("9.14"), "USD"),
            new ShippoClient.Rate("r_ups", "UPS", "Ground", new BigDecimal("12.40"), "USD"),
            new ShippoClient.Rate("r_usps_ground", "USPS", "Ground Advantage", new BigDecimal("7.58"), "USD"),
            new ShippoClient.Rate("r_fedex_2day", "FedEx", "2Day", new BigDecimal("24.10"), "USD"),
            new ShippoClient.Rate("r_fedex", "FedEx", "Ground", new BigDecimal("11.95"), "USD"));

    private final ShippoClient client = mock(ShippoClient.class);
    private final ShippoShippingProvider shippo = new ShippoShippingProvider(client, FROM, BOX, 16);

    private static ShippoClient.Transaction ok(String tracking) {
        return new ShippoClient.Transaction("txn_" + tracking, "SUCCESS", tracking, "https://label/" + tracking + ".pdf", "");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"USPS", "FEDEX", "UPS"})
    void theCheapestRateOfTheChosenCarrierIsBought(String carrier) {
        when(client.rates(any(), any(), any(), anyString())).thenReturn(RATES);
        String expected = switch (carrier) { case "USPS" -> "r_usps_ground"; case "FEDEX" -> "r_fedex"; default -> "r_ups"; };
        when(client.buy(expected, "Order ORD-1")).thenReturn(ok("T1"));

        Label l = shippo.buyLabel(new LabelRequest("ORD-1", carrier, TO, 3));

        assertThat(l.trackingNumber()).isEqualTo("T1");
        assertThat(l.labelUrl()).isEqualTo("https://label/T1.pdf");
        assertThat(l.reference()).isEqualTo("txn_T1");
        assertThat(l.cost()).isEqualByComparingTo(switch (carrier) { case "USPS" -> "7.58"; case "FEDEX" -> "11.95"; default -> "12.40"; });
    }

    @Test
    void theParcelWeighsTheItemsTimesTheOuncesPerItemAndAtLeastOneItem() {
        when(client.rates(any(), any(), any(), anyString())).thenReturn(RATES);
        when(client.buy(anyString(), anyString())).thenReturn(ok("T"));
        shippo.buyLabel(new LabelRequest("ORD-1", "USPS", TO, 3));
        verify(client).rates(FROM, TO, new ShippoClient.Parcel("10", "8", "4", "48"), "Order ORD-1");
        shippo.buyLabel(new LabelRequest("ORD-2", "USPS", TO, 0));
        verify(client).rates(FROM, TO, new ShippoClient.Parcel("10", "8", "4", "16"), "Order ORD-2");
    }

    @Test
    void noRateForTheCarrierIs400AndNothingIsBought() {
        when(client.rates(any(), any(), any(), anyString())).thenReturn(List.of(RATES.get(1)));
        assertThatThrownBy(() -> shippo.buyLabel(new LabelRequest("ORD-1", "USPS", TO, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("No USPS rate for this parcel and address. Choose another carrier or check the address.");
        verify(client, never()).buy(anyString(), anyString());
    }

    @Test
    void aLabelTheCarrierRefusesIs400WithItsReason() {
        when(client.rates(any(), any(), any(), anyString())).thenReturn(RATES);
        when(client.buy(anyString(), anyString()))
                .thenReturn(new ShippoClient.Transaction("t", "ERROR", null, null, "Address not found"))
                .thenReturn(new ShippoClient.Transaction("t", "QUEUED", null, null, " "))
                .thenReturn(new ShippoClient.Transaction("t", "ERROR", null, null, null));
        assertThatThrownBy(() -> shippo.buyLabel(new LabelRequest("ORD-1", "UPS", TO, 1)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("The label could not be bought: Address not found");
        assertThatThrownBy(() -> shippo.buyLabel(new LabelRequest("ORD-1", "UPS", TO, 1)))
                .hasMessage("The label could not be bought: no reason given");
        assertThatThrownBy(() -> shippo.buyLabel(new LabelRequest("ORD-1", "UPS", TO, 1)))
                .hasMessage("The label could not be bought: no reason given");
    }

    @Test
    void aRequestShippoRejectsIs400AndAnOutageIs503() {
        when(client.rates(any(), any(), any(), anyString()))
                .thenThrow(new ShippoException(400, "zip: This field may not be blank."))
                .thenThrow(new ShippoException(0, "down"))
                .thenThrow(new ShippoException(401, "Invalid token."));
        assertThatThrownBy(() -> shippo.buyLabel(new LabelRequest("ORD-1", "UPS", TO, 1)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("The carrier refused the shipment: zip: This field may not be blank.");
        assertThatThrownBy(() -> shippo.buyLabel(new LabelRequest("ORD-1", "UPS", TO, 1)))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage(ShippoShippingProvider.UNAVAILABLE);
        assertThatThrownBy(() -> shippo.buyLabel(new LabelRequest("ORD-1", "UPS", TO, 1)))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void anOrderWithoutAnAddressGetsNoLabel() {
        assertThatThrownBy(() -> shippo.buyLabel(new LabelRequest("ORD-1", "UPS", null, 1)))
                .isInstanceOf(BusinessRuleException.class).hasMessage(ShippoShippingProvider.NO_ADDRESS);
        verifyNoInteractions(client);
    }

    @Test
    void trackingIsAskedWithShipposLowerCaseCarrierToken() {
        when(client.trackingStatus(eq("fedex"), eq("7712"))).thenReturn("TRANSIT");
        assertThat(shippo.trackingStatus("FEDEX", "7712")).isEqualTo("TRANSIT");
        assertThat(shippo.name()).isEqualTo("shippo");
    }

    @Test
    void shipposProviderNamesBecomeTheShopsCarrierCodes() {
        assertThat(ShippoShippingProvider.carrierCode("FedEx")).isEqualTo("FEDEX");
        assertThat(ShippoShippingProvider.carrierCode("USPS")).isEqualTo("USPS");
        assertThat(ShippoShippingProvider.carrierCode(null)).isEmpty();
    }
}
