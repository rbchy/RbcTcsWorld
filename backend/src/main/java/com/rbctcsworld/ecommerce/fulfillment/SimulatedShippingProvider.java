package com.rbctcsworld.ecommerce.fulfillment;

import com.rbctcsworld.ecommerce.order.OrderRepository;

import java.security.SecureRandom;

/** The default: no carrier account, no money. A unique made-up tracking number, no label. */
class SimulatedShippingProvider implements ShippingProvider {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orders;

    SimulatedShippingProvider(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public Label buyLabel(LabelRequest request) {
        return new Label(newTrackingNumber(request.carrier()), null, null, null);
    }

    /** e.g. UPS-482910384756 (carrier + 12 random digits, unique). */
    String newTrackingNumber(String carrier) {
        String tn;
        do {
            StringBuilder sb = new StringBuilder(carrier).append('-');
            for (int i = 0; i < 12; i++) sb.append(RANDOM.nextInt(10));
            tn = sb.toString();
        } while (orders.existsByTrackingNumber(tn));
        return tn;
    }
}
