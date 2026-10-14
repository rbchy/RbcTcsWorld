package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImagePolicyTest {

    private static ProductImagePolicy policy(boolean demo) {
        return new ProductImagePolicy(new MockEnvironment().withProperty("app.demo.enabled", String.valueOf(demo)));
    }

    @Test
    void outsideLinksAreAllowedInANormalShop() {
        assertThatCode(() -> policy(false).check("https://cdn.example.com/a.png")).doesNotThrowAnyException();
        assertThatCode(() -> new ProductImagePolicy(new MockEnvironment()).check("https://cdn.example.com/a.png"))
                .as("demo is off by default").doesNotThrowAnyException();
    }

    @Test
    void theDemoRefusesOutsideLinks() {
        assertThatThrownBy(() -> policy(true).check("https://cdn.example.com/a.png"))
                .isInstanceOf(BusinessRuleException.class).hasMessage(ProductImagePolicy.DEMO_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/products/home-mug-002.svg", " /products/x.png "})
    void theDemoAcceptsTheShopsOwnPictures(String url) {
        assertThatCode(() -> policy(true).check(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void noPictureIsAlwaysFine(String url) {
        assertThatCode(() -> policy(true).check(url)).doesNotThrowAnyException();
    }
}
