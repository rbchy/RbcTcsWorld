package com.rbctcsworld.ecommerce.fulfillment;

import java.math.BigDecimal;

/**
 * Where shipping labels and tracking come from. app.shipping.provider chooses:
 *  - simulated (default): a made-up tracking number such as UPS-482910384756, no label, no money; as before.
 *  - shippo: a real label bought through Shippo (USPS, UPS, FedEx) at the carrier's cheapest rate for the parcel,
 *    and the real tracking number; Shippo then reports the parcel's progress by webhook.
 * What the customer pays for shipping does not change: that is the store's price (PricingService).
 */
public interface ShippingProvider {

    /** Short name: simulated | shippo. */
    String name();

    /** 400 no rate for that carrier or an address the carrier refuses; 503 the provider does not answer. */
    Label buyLabel(LabelRequest request);

    /** The carrier's current status (DELIVERED, TRANSIT, ...), read from the provider; null when it has none. */
    default String trackingStatus(String carrier, String trackingNumber) {
        return null;
    }

    record Address(String name, String street1, String street2, String city, String state, String zip, String phone) {
    }

    /** carrier: UPS | FEDEX | USPS as chosen by the warehouse; items: how many units go in the parcel. */
    record LabelRequest(String orderNumber, String carrier, Address to, int items) {
    }

    /** labelUrl, cost and reference are null for the simulated provider. */
    record Label(String trackingNumber, String labelUrl, BigDecimal cost, String reference) {
    }
}
