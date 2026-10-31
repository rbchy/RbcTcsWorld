package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.audit.AuditChange;
import com.rbctcsworld.ecommerce.audit.AuditEvent;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REQ-FUL-06: a product's shipping weight and box size (ADR-0013). */
@ExtendWith(MockitoExtension.class)
class ProductParcelTest {

    @Mock ProductRepository repo;
    @Mock ApplicationEventPublisher events;
    @Mock com.rbctcsworld.ecommerce.inventory.InventoryService inventory;
    @InjectMocks ProductService service;

    private static BigDecimal d(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static ProductRequest request(String oz, String l, String w, String h) {
        return new ProductRequest("Trail shoe", "SH-1", "shoes", new BigDecimal("59.99"), 3, null, null, null, null,
                null, null, null, null, null, d(oz), d(l), d(w), d(h));
    }

    private static Product existing() {
        Product p = new Product("Trail shoe", "SH-1", "shoes", new BigDecimal("59.99"), 3);
        ReflectionTestUtils.setField(p, "id", 7L);
        return p;
    }

    @Test
    void aNewProductKeepsItsWeightAndBoxWithOneDecimalAndTheAuditTrailRecordsThem() {
        when(repo.save(any(Product.class))).thenAnswer(call -> {
            Product saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });

        Product p = service.create(request("28", "13", "8", "4.5"));

        assertThat(p.getWeightOz()).isEqualTo(new BigDecimal("28.0"));
        assertThat(List.of(p.getLengthIn(), p.getWidthIn(), p.getHeightIn()))
                .containsExactly(new BigDecimal("13.0"), new BigDecimal("8.0"), new BigDecimal("4.5"));
        verify(events).publishEvent(org.mockito.ArgumentMatchers.<AuditEvent>argThat(e -> e.changes().stream().anyMatch(c ->
                c.field().equals("weightOz") && "28.0".equals(c.newValue()))));
    }

    @Test
    void anUpdateChangesOrRemovesThemAndIsAudited() {
        Product p = existing();
        p.changeParcel(d("28"), d("13"), d("8"), d("5"));
        when(repo.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(p));
        when(repo.findBySku("SH-1")).thenReturn(Optional.of(p));
        when(repo.save(p)).thenReturn(p);

        service.update(7L, request("30", "13", "8", "5"));
        assertThat(p.getWeightOz()).isEqualByComparingTo("30");
        verify(events).publishEvent(AuditEvent.of(List.of(
                new AuditChange("PRODUCT", 7L, "PRODUCT_UPDATED", "weightOz", "28.0", "30.0"))));

        service.update(7L, request(null, null, null, null));                // PUT replaces: none = the defaults
        assertThat(List.of(java.util.Optional.ofNullable(p.getWeightOz()), java.util.Optional.ofNullable(p.getLengthIn()),
                java.util.Optional.ofNullable(p.getWidthIn()), java.util.Optional.ofNullable(p.getHeightIn())))
                .allMatch(java.util.Optional::isEmpty);
    }

    @Test
    void aBoxNeedsAllThreeSidesOrNone() {
        assertThatThrownBy(() -> service.create(request(null, "13", "8", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage(ProductService.BOX_SIZE);
        assertThatThrownBy(() -> service.create(request("10", null, null, "4")))
                .isInstanceOf(BusinessRuleException.class);
        verify(repo, never()).save(any());

        Product p = existing();
        when(repo.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(p));
        when(repo.findBySku("SH-1")).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.update(7L, request(null, "13", null, null))).isInstanceOf(BusinessRuleException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void aWeightAloneIsFineTheBoxStaysTheDefault() {
        ProductService.checkBoxSize(request("12", null, null, null));
        ProductService.checkBoxSize(request(null, "1", "1", "1"));
    }
}
