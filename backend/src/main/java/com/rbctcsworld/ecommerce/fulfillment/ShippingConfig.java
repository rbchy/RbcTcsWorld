package com.rbctcsworld.ecommerce.fulfillment;

import com.rbctcsworld.ecommerce.order.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.Arrays;

/**
 * app.shipping.provider = simulated (default) | shippo. Like payments, the start stops for a typo, a missing or
 * malformed token, and a LIVE token (shippo_live_: real labels cost real money) outside the 'prod' profile.
 */
@Configuration
class ShippingConfig {

    @Bean
    ShippingProvider shippingProvider(@Value("${app.shipping.provider:simulated}") String provider,
                                      @Value("${app.shipping.shippo.api-base:https://api.goshippo.com}") String apiBase,
                                      @Value("${app.shipping.shippo.token:}") String token,
                                      @Value("${app.shipping.shippo.timeout-seconds:20}") long timeoutSeconds,
                                      @Value("${app.shipping.from.name}") String fromName,
                                      @Value("${app.shipping.from.street}") String fromStreet,
                                      @Value("${app.shipping.from.city}") String fromCity,
                                      @Value("${app.shipping.from.state}") String fromState,
                                      @Value("${app.shipping.from.zip}") String fromZip,
                                      @Value("${app.shipping.from.phone}") String fromPhone,
                                      @Value("${app.shipping.parcel.length-in:10}") String length,
                                      @Value("${app.shipping.parcel.width-in:8}") String width,
                                      @Value("${app.shipping.parcel.height-in:4}") String height,
                                      @Value("${app.shipping.parcel.ounces-per-item:16}") int ouncesPerItem,
                                      OrderRepository orders, Environment env) {
        boolean prod = Arrays.asList(env.getActiveProfiles()).contains("prod");
        ShippingProvider.Address from = new ShippingProvider.Address(fromName, fromStreet, null, fromCity, fromState, fromZip, fromPhone);
        return create(provider, apiBase, token, Duration.ofSeconds(timeoutSeconds), prod, from,
                new ShippoClient.Parcel(length, width, height, null), ouncesPerItem, orders);
    }

    static ShippingProvider create(String provider, String apiBase, String token, Duration timeout, boolean prod,
                                   ShippingProvider.Address from, ShippoClient.Parcel box, int ouncesPerItem,
                                   OrderRepository orders) {
        return switch (provider.trim().toLowerCase()) {
            case "simulated" -> new SimulatedShippingProvider(orders);
            case "shippo" -> {
                if (token == null || !(token.startsWith("shippo_test_") || token.startsWith("shippo_live_"))) {
                    throw new IllegalStateException("app.shipping.provider=shippo needs SHIPPO_TOKEN (shippo_test_...)");
                }
                if (token.startsWith("shippo_live_") && !prod) {
                    throw new IllegalStateException("Refusing a LIVE Shippo token outside the 'prod' profile: live labels cost money");
                }
                if (ouncesPerItem < 1) {
                    throw new IllegalStateException("app.shipping.parcel.ounces-per-item must be at least 1");
                }
                yield new ShippoShippingProvider(new ShippoClient(apiBase, token, timeout), from, box, ouncesPerItem);
            }
            default -> throw new IllegalStateException(
                    "app.shipping.provider must be 'simulated' or 'shippo', not '" + provider + "'");
        };
    }
}
