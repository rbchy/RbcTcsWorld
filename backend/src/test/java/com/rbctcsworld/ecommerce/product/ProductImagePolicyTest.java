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

    @Test
    void photoUploadsAreOnlyRefusedInTheDemo() {
        assertThatCode(() -> policy(false).checkUpload()).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy(true).checkUpload())
                .isInstanceOf(BusinessRuleException.class).hasMessage(ProductImagePolicy.DEMO_UPLOAD_MESSAGE);
    }

    @Test
    void partnerLinksAreOnlyRefusedInTheDemo() {
        assertThatCode(() -> policy(false).checkAffiliate("https://www.amazon.com/dp/B000")).doesNotThrowAnyException();
        assertThatCode(() -> policy(true).checkAffiliate(" ")).doesNotThrowAnyException();
        assertThatCode(() -> policy(true).checkAffiliate(null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy(true).checkAffiliate("https://www.amazon.com/dp/B000"))
                .isInstanceOf(BusinessRuleException.class).hasMessage(ProductImagePolicy.DEMO_AFFILIATE_MESSAGE);
    }
}
