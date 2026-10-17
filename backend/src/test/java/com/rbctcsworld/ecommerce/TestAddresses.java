package com.rbctcsworld.ecommerce;

import com.rbctcsworld.ecommerce.order.ShippingAddress;

/** A valid delivery address for tests that place orders (every order needs one since V12). */
public final class TestAddresses {

    private TestAddresses() {
    }

    public static final ShippingAddress ADDRESS =
            new ShippingAddress("Ada Lovelace", "12 Main St", "Apt 4", "Pittsburgh", "PA", "15213", "412-555-0100");

    /** The address as a JSON property, to put inside a request body. */
    public static final String JSON = "\"shippingAddress\":{\"fullName\":\"Ada Lovelace\",\"line1\":\"12 Main St\","
            + "\"line2\":\"Apt 4\",\"city\":\"Pittsburgh\",\"state\":\"PA\",\"zip\":\"15213\",\"phone\":\"412-555-0100\"}";

    /** Body of POST /api/orders without a coupon. */
    public static final String ORDER_BODY = "{" + JSON + "}";

    /** Body of POST /api/orders with a coupon (null = no coupon). */
    public static String orderBody(String coupon) {
        return coupon == null ? ORDER_BODY : "{\"couponCode\":\"" + coupon + "\"," + JSON + "}";
    }
}
