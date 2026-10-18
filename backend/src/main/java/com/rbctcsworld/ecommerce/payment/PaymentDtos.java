package com.rbctcsworld.ecommerce.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** Card details exist only in memory for the duration of the request - never logged, never stored. */
    public record PayRequest(
            @NotBlank @Pattern(regexp = "[0-9 ]{13,23}", message = "must be 13-19 digits") String cardNumber,
            @NotNull @Min(1) @Max(12) Integer expiryMonth,
            @NotNull @Min(2000) @Max(2100) Integer expiryYear,
            @NotBlank @Pattern(regexp = "\\d{3,4}", message = "must be 3 or 4 digits") String cvv,
            Boolean saveCard) {

        /** Without "save this card" (older clients, tests). */
        public PayRequest(String cardNumber, Integer expiryMonth, Integer expiryYear, String cvv) {
            this(cardNumber, expiryMonth, expiryYear, cvv, null);
        }

        @Override
        public String toString() {   // protects against accidental logging of card data
            return "PayRequest[card=****, expiry=**/****, cvv=***]";
        }
    }

    /** Pay with a saved card: the CVV is asked again every time and never kept. */
    public record PaySavedRequest(
            @NotNull Long paymentMethodId,
            @NotBlank @Pattern(regexp = "\\d{3,4}", message = "must be 3 or 4 digits") String cvv) {

        @Override
        public String toString() {
            return "PaySavedRequest[paymentMethodId=" + paymentMethodId + ", cvv=***]";
        }
    }

    /** Add a card to the wallet. The number is turned into the provider's token and then forgotten. */
    public record AddCardRequest(
            @NotBlank @Pattern(regexp = "[0-9 ]{13,23}", message = "must be 13-19 digits") String cardNumber,
            @NotNull @Min(1) @Max(12) Integer expiryMonth,
            @NotNull @Min(2000) @Max(2100) Integer expiryYear,
            boolean makeDefault) {

        @Override
        public String toString() {
            return "AddCardRequest[card=****, expiry=**/****]";
        }
    }

    public record MethodResponse(long id, String brand, String last4, int expiryMonth, int expiryYear, boolean expired, boolean isDefault) {

        static MethodResponse from(SavedPaymentMethod m, java.time.YearMonth now) {
            return new MethodResponse(m.getId(), m.getBrand(), m.getLast4(), m.getExpiryMonth(), m.getExpiryYear(),
                    m.expiredAt(now), m.isDefault());
        }
    }
}
