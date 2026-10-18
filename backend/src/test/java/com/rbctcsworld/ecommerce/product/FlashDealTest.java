package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Flash deals: REQ-CAT-14. */
@ExtendWith(MockitoExtension.class)
class FlashDealTest {

    @Mock ProductRepository repo;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @Mock com.rbctcsworld.ecommerce.inventory.InventoryService inventory;
    @InjectMocks ProductService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0, 0);

    private static Product lamp() {
        return new Product("Lamp", "LAMP-1", "home", new BigDecimal("40.00"), 5);
    }

    private static ProductRequest req(String price, String dealPrice, LocalDateTime from, LocalDateTime to) {
        return new ProductRequest("Lamp", "LAMP-1", "home", new BigDecimal(price), 5, null, null, null, null,
                dealPrice == null ? null : new BigDecimal(dealPrice), from, to);
    }

    private static LocalDateTime hours(long h) {
        return LocalDateTime.now(ZoneOffset.UTC).plusHours(h);
    }

    @Test
    void theDealPriceCountsOnlyInsideTheWindow() {
        Product p = lamp();
        p.changeDeal(new BigDecimal("25.00"), NOW, NOW.plusHours(2));
        assertThat(p.priceAt(NOW.minusSeconds(1))).isEqualByComparingTo("40.00");
        assertThat(p.priceAt(NOW)).isEqualByComparingTo("25.00");
        assertThat(p.priceAt(NOW.plusHours(2).minusSeconds(1))).isEqualByComparingTo("25.00");
        assertThat(p.priceAt(NOW.plusHours(2))).isEqualByComparingTo("40.00");   // end is exclusive
    }

    @Test
    void aProductWithoutADealHasNoDealFlag() {
        Product p = lamp();
        assertThat(p.getDealLive()).isNull();
        assertThat(p.currentPrice()).isEqualByComparingTo("40.00");
    }

    @Test
    void clearingTheDealRestoresTheNormalPrice() {
        Product p = lamp();
        p.changeDeal(new BigDecimal("25.00"), hours(-1), hours(1));
        assertThat(p.currentPrice()).isEqualByComparingTo("25.00");
        p.changeDeal(null, null, null);
        assertThat(p.currentPrice()).isEqualByComparingTo("40.00");
        assertThat(p.getDealPrice()).isNull();
    }

    @Test
    void aGoodDealPasses() {
        assertThatCode(() -> service.checkDeal(req("40.00", "25.00", hours(-1), hours(2)), null)).doesNotThrowAnyException();
    }

    @Test
    void noDealFieldsMeansNoCheck() {
        assertThatCode(() -> service.checkDeal(req("40.00", null, null, null), null)).doesNotThrowAnyException();
    }

    @Test
    void halfADealIsRefused() {
        assertThatThrownBy(() -> service.checkDeal(req("40.00", null, hours(0), hours(2)), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("needs a deal price");
        assertThatThrownBy(() -> service.checkDeal(req("40.00", "25.00", hours(0), null), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("start and an end");
    }

    @Test
    void theDealPriceMustBeLowerThanThePrice() {
        assertThatThrownBy(() -> service.checkDeal(req("40.00", "40.00", hours(0), hours(2)), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("lower than the price");
        assertThatThrownBy(() -> service.checkDeal(req("40.00", "41.00", hours(0), hours(2)), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void theDealMustEndAfterItStartsAndLastAtMostThirtyDays() {
        LocalDateTime start = hours(1);                                   // one fixed start: two now() calls would differ
        assertThatThrownBy(() -> service.checkDeal(req("40.00", "25.00", start, start), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("end after it starts");
        assertThatThrownBy(() -> service.checkDeal(req("40.00", "25.00", start, start.minusMinutes(1)), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("end after it starts");
        assertThatCode(() -> service.checkDeal(req("40.00", "25.00", start, start.plusDays(30)), null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.checkDeal(req("40.00", "25.00", start, start.plusDays(30).plusSeconds(1)), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("30 days");
    }

    @Test
    void aNewDealThatIsAlreadyOverIsRefusedButAnUnchangedOldOneIsKept() {
        LocalDateTime from = hours(-5), to = hours(-2);
        assertThatThrownBy(() -> service.checkDeal(req("40.00", "25.00", from, to), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already ended");
        Product stored = lamp();
        stored.changeDeal(new BigDecimal("25.00"), from, to);
        assertThatCode(() -> service.checkDeal(req("40.00", "25.00", from, to), stored)).doesNotThrowAnyException();
        // changing the price of that over deal counts as a new deal
        assertThatThrownBy(() -> service.checkDeal(req("40.00", "20.00", from, to), stored))
                .isInstanceOf(BusinessRuleException.class);
    }
}
