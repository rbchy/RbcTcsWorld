package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.audit.AuditChange;
import com.rbctcsworld.ecommerce.audit.AuditEvent;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REQ-PAY-13: a product's own Stripe Tax code (ADR-0012). */
@ExtendWith(MockitoExtension.class)
class ProductTaxCodeTest {

    @Mock ProductRepository repo;
    @Mock ApplicationEventPublisher events;
    @Mock com.rbctcsworld.ecommerce.inventory.InventoryService inventory;
    @InjectMocks ProductService service;

    private static ProductRequest request(String taxCode) {
        return new ProductRequest("Classic tee", "TEE-1", "clothing", new BigDecimal("19.99"), 3, null, null, null, null,
                null, null, null, null, null, null, null, null, null, taxCode);
    }

    @Test
    void theCodeIsKeptTrimmedInLowerCaseAndAudited() {
        when(repo.save(any(Product.class))).thenAnswer(call -> {
            Product saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });

        Product p = service.create(request("  TXCD_30011000 "));

        assertThat(p.getTaxCode()).isEqualTo("txcd_30011000");
        verify(events).publishEvent(org.mockito.ArgumentMatchers.<AuditEvent>argThat(e -> e.changes().stream().anyMatch(c ->
                c.field().equals("taxCode") && "txcd_30011000".equals(c.newValue()))));
    }

    @Test
    void anUpdateChangesOrRemovesTheCodeAndIsAudited() {
        Product p = new Product("Classic tee", "TEE-1", "clothing", new BigDecimal("19.99"), 3);
        ReflectionTestUtils.setField(p, "id", 7L);
        p.changeTaxCode("txcd_99999999");
        when(repo.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(p));
        when(repo.findBySku("TEE-1")).thenReturn(Optional.of(p));
        when(repo.save(p)).thenReturn(p);

        service.update(7L, request("txcd_30011000"));
        assertThat(p.getTaxCode()).isEqualTo("txcd_30011000");
        verify(events).publishEvent(AuditEvent.of(List.of(
                new AuditChange("PRODUCT", 7L, "PRODUCT_UPDATED", "taxCode", "txcd_99999999", "txcd_30011000"))));

        service.update(7L, request("   "));                        // blank = the account's default code
        assertThat(p.getTaxCode()).isNull();
    }
}
