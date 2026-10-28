package com.rbctcsworld.ecommerce.fulfillment;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Shippo: asks every carrier's rates for the parcel, takes the cheapest rate of the carrier the warehouse chose, and
 * buys that label. A carrier with no rate, or an address it refuses, is 400; Shippo not answering is 503. Either way no
 * label is bought and the order stays PAID.
 */
class ShippoShippingProvider implements ShippingProvider {

    static final String NO_ADDRESS = "This order has no delivery address, so no label can be bought";
    static final String UNAVAILABLE = "The shipping service is not available right now. Please try again in a moment.";
    private static final Logger log = LoggerFactory.getLogger(ShippoShippingProvider.class);

    private final ShippoClient shippo;
    private final Address from;
    private final ShippoClient.Parcel box;
    private final int ouncesPerItem;

    ShippoShippingProvider(ShippoClient shippo, Address from, ShippoClient.Parcel box, int ouncesPerItem) {
        this.shippo = shippo;
        this.from = from;
        this.box = box;
        this.ouncesPerItem = ouncesPerItem;
    }

    @Override
    public String name() {
        return "shippo";
    }

    @Override
    public Label buyLabel(LabelRequest request) {
        if (request.to() == null) {
            throw new BusinessRuleException(NO_ADDRESS);   // orders from before addresses were required
        }
        String metadata = "Order " + request.orderNumber();
        ShippoClient.Parcel parcel = new ShippoClient.Parcel(box.length(), box.width(), box.height(),
                Integer.toString(Math.max(1, request.items()) * ouncesPerItem));
        try {
            List<ShippoClient.Rate> rates = shippo.rates(from, request.to(), parcel, metadata);
            ShippoClient.Rate cheapest = rates.stream()
                    .filter(r -> carrierCode(r.provider()).equals(request.carrier()))
                    .min(Comparator.comparing(ShippoClient.Rate::amount))
                    .orElseThrow(() -> new BusinessRuleException("No " + request.carrier()
                            + " rate for this parcel and address. Choose another carrier or check the address."));
            ShippoClient.Transaction t = shippo.buy(cheapest.id(), metadata);
            if (!"SUCCESS".equals(t.status())) {
                throw new BusinessRuleException("The label could not be bought: "
                        + (t.messages() == null || t.messages().isBlank() ? "no reason given" : t.messages()));
            }
            log.info("Order {}: {} {} label {} for {} {}", request.orderNumber(), cheapest.provider(), cheapest.service(),
                    t.trackingNumber(), cheapest.amount(), cheapest.currency());
            return new Label(t.trackingNumber(), t.labelUrl(), cheapest.amount(), t.id());
        } catch (ShippoException e) {
            if (e.status() == 400) {
                throw new BusinessRuleException("The carrier refused the shipment: " + e.getMessage());
            }
            log.error("Shippo failed for order {} (HTTP {}): {}", request.orderNumber(), e.status(), e.getMessage());
            throw new ServiceUnavailableException(UNAVAILABLE);
        }
    }

    @Override
    public String trackingStatus(String carrier, String trackingNumber) {
        return shippo.trackingStatus(carrier.toLowerCase(Locale.ROOT), trackingNumber);
    }

    /** Shippo's provider names (USPS, UPS, FedEx) as the shop's carrier codes (USPS, UPS, FEDEX). */
    static String carrierCode(String provider) {
        return provider == null ? "" : provider.toUpperCase(Locale.ROOT);
    }
}
