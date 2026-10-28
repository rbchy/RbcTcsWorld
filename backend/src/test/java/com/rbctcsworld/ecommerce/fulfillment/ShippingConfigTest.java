package com.rbctcsworld.ecommerce.fulfillment;

import com.rbctcsworld.ecommerce.order.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Which shipping provider starts, and the start refusals (typo, no token, live token outside prod). */
class ShippingConfigTest {

    private static final ShippingProvider.Address FROM = new ShippingProvider.Address("S", "1 St", null, "C", "PA", "19107", "1");
    private static final ShippoClient.Parcel BOX = new ShippoClient.Parcel("10", "8", "4", null);
    private final OrderRepository orders = mock(OrderRepository.class);

    private ShippingProvider create(String provider, String token, boolean prod, int ounces) {
        return ShippingConfig.create(provider, "https://api.goshippo.com", token, Duration.ofSeconds(5), prod, FROM, BOX, ounces, orders);
    }

    @Test
    void simulatedIsTheDefaultAndNeedsNoToken() {
        assertThat(create(" Simulated ", null, false, 16)).isInstanceOf(SimulatedShippingProvider.class);
    }

    @Test
    void shippoNeedsATestOrLiveToken() {
        assertThat(create("shippo", "shippo_test_1", false, 16)).isInstanceOf(ShippoShippingProvider.class);
        for (String bad : new String[] {null, "", "sk_test_1", "shippo_1"}) {
            assertThatThrownBy(() -> create("shippo", bad, false, 16)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHIPPO_TOKEN");
        }
    }

    @Test
    void aLiveTokenIsOnlyAcceptedInProd() {
        assertThatThrownBy(() -> create("shippo", "shippo_live_1", false, 16)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVE");
        assertThat(create("shippo", "shippo_live_1", true, 16)).isInstanceOf(ShippoShippingProvider.class);
    }

    @Test
    void aParcelMustWeighSomething() {
        assertThatThrownBy(() -> create("shippo", "shippo_test_1", false, 0)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ounces-per-item");
        assertThat(create("shippo", "shippo_test_1", false, 1)).isInstanceOf(ShippoShippingProvider.class);
    }

    @Test
    void aTypoStopsTheStart() {
        assertThatThrownBy(() -> create("easypost", null, false, 16)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'easypost'");
    }

    @Test
    void theSpringBeanReadsTheProdProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        ShippingConfig config = new ShippingConfig();
        assertThat(config.shippingProvider("shippo", "https://api.goshippo.com", "shippo_live_1", 20, "S", "1 St", "C", "PA",
                "19107", "1", "10", "8", "4", 16, orders, prod)).isInstanceOf(ShippoShippingProvider.class);
        assertThatThrownBy(() -> config.shippingProvider("shippo", "https://api.goshippo.com", "shippo_live_1", 20, "S", "1 St",
                "C", "PA", "19107", "1", "10", "8", "4", 16, orders, new MockEnvironment())).isInstanceOf(IllegalStateException.class);
    }
}
