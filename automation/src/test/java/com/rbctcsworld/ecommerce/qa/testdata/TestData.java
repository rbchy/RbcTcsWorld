package com.rbctcsworld.ecommerce.qa.testdata;

import java.util.UUID;

/** Unique, self-cleaning-free test data: every run creates its own users/SKUs, so tests never collide. */
public final class TestData {

    public static final String PASSWORD = "Password1!";

    /** Seeded by backend Flyway V2 - stable SKUs the tests can rely on. */
    public static final String SKU_MOUSE = "ELEC-MOUSE-001";        // stock 150
    public static final String SKU_LIMITED_HEADPHONES = "ELEC-HEAD-005"; // stock 3

    /** Seeded coupons (backend Flyway V5). */
    public static final String COUPON_WELCOME10 = "WELCOME10"; // 10%, once per customer
    public static final String COUPON_SAVE5 = "SAVE5";         // $5 off, min order $25
    public static final String COUPON_EXPIRED = "EXPIRED20";
    public static final String COUPON_FUTURE = "FUTURE15";
    public static final String COUPON_DISABLED = "DISABLED";

    /** Mock gateway test cards (same idea as Stripe test cards). */
    public static final String CARD_OK = "4242424242424242";
    public static final String CARD_DECLINED = "4000000000000002";
    public static final String CARD_NO_FUNDS = "4000000000009995";
    public static final String CARD_BAD_LUHN = "4242424242424241";

    /** A valid US delivery address (every order needs one). Pittsburgh, PA: 6 % sales tax like the shop. */
    public static final java.util.Map<String, String> ADDRESS = java.util.Map.of(
            "fullName", "Ada Lovelace", "line1", "12 Main St", "line2", "Apt 4", "city", "Pittsburgh",
            "state", "PA", "zip", "15213", "phone", "412-555-0100");

    private TestData() {
    }

    public static String uniqueEmail() {
        return "qa-" + UUID.randomUUID().toString().substring(0, 12) + "@test.rbctcsworld.com";
    }

    public static String uniqueCouponCode() {
        return "QA" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static String uniqueSku() {
        return "QA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
