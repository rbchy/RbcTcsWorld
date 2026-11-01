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

    /**
     * A prepaid return label (ADR-0023): from the customer's address (request.to) back to the store, at the cheapest
     * rate of any carrier (request.carrier is ignored). 400 when this provider cannot buy return labels.
     */
    default ReturnLabel buyReturnLabel(LabelRequest request) {
        throw new com.rbctcsworld.ecommerce.common.exception.BusinessRuleException(NO_RETURN_LABELS);
    }

    String NO_RETURN_LABELS = "Return labels need a shipping provider with real labels (SHIPPING_PROVIDER=shippo)";

    /** The carrier's current status (DELIVERED, TRANSIT, ...), read from the provider; null when it has none. */
    default String trackingStatus(String carrier, String trackingNumber) {
        return null;
    }

    record Address(String name, String street1, String street2, String city, String state, String zip, String phone) {
    }

    /**
     * carrier: UPS | FEDEX | USPS as chosen by the warehouse; items: how many units go in the parcel; pieces: the
     * order's lines with each product's own weight and box size where the admin gave them (empty = the defaults).
     */
    record LabelRequest(String orderNumber, String carrier, Address to, int items, java.util.List<Piece> pieces) {

        public LabelRequest {
            pieces = pieces == null ? java.util.List.of() : java.util.List.copyOf(pieces);
        }

        /** Only a count of units: every unit weighs the configured ounces and the parcel is the configured box. */
        public LabelRequest(String orderNumber, String carrier, Address to, int items) {
            this(orderNumber, carrier, to, items, java.util.List.of());
        }
    }

    /** One order line: quantity units of a product; weightOz and the box sides (inches) are null when not given. */
    record Piece(int quantity, BigDecimal weightOz, BigDecimal lengthIn, BigDecimal widthIn, BigDecimal heightIn) {

        boolean hasBox() {
            return lengthIn != null && widthIn != null && heightIn != null;
        }
    }

    /** carrier: USPS | UPS | FEDEX, the one whose rate was cheapest. */
    record ReturnLabel(String carrier, String trackingNumber, String labelUrl, BigDecimal cost, String reference) {
    }

    /** labelUrl, cost and reference are null for the simulated provider. */
    record Label(String trackingNumber, String labelUrl, BigDecimal cost, String reference) {
    }
}
