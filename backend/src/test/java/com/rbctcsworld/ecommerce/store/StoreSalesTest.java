package com.rbctcsworld.ecommerce.store;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REQ-AFF-04 (ADR-0026): STORE_SALES is on or off, nothing in between. */
class StoreSalesTest {

    @ParameterizedTest
    @ValueSource(strings = {"on", " ON ", "true", "True"})
    void onMeansTheFullShop(String setting) {
        assertThat(new StoreSales(setting).on()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"off", " Off ", "false", "FALSE"})
    void offMeansAPartnerSite(String setting) {
        assertThat(new StoreSales(setting).on()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "no", "partner", "0"})
    void aTypoStopsTheStart(String setting) {
        assertThatThrownBy(() -> new StoreSales(setting)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STORE_SALES must be 'on' or 'off'");
    }

    @Test
    void nullIsATypoToo() {
        assertThatThrownBy(() -> new StoreSales(null)).isInstanceOf(IllegalStateException.class);
        assertThat(StoreSales.of(true).on()).isTrue();
        assertThat(StoreSales.of(false).on()).isFalse();
    }
}
