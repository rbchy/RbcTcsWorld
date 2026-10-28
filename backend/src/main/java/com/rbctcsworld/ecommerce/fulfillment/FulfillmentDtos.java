package com.rbctcsworld.ecommerce.fulfillment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public final class FulfillmentDtos {

    private FulfillmentDtos() {
    }

    public record ShipRequest(
            @NotBlank @Pattern(regexp = "UPS|FEDEX|USPS", message = "must be UPS, FEDEX or USPS") String carrier) {
    }

    /** The bought label: print labelUrl (PDF); cost is what the carrier charged the store. */
    public record LabelResponse(String carrier, String trackingNumber, String labelUrl, BigDecimal cost, String provider) {
    }
}
