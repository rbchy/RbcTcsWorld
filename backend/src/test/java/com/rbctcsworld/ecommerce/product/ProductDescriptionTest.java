package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.audit.AuditChange;
import com.rbctcsworld.ecommerce.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

/** REQ-CAT-16: the owner's own description of a product (ADR-0021). */
@ExtendWith(MockitoExtension.class)
class ProductDescriptionTest {

    @Mock ProductRepository repo;
    @Mock ApplicationEventPublisher events;
    @Mock com.rbctcsworld.ecommerce.inventory.InventoryService inventory;
    @InjectMocks ProductService service;

    private static ProductRequest request(String description) {
        return new ProductRequest("Trail shoe", "SH-1", "shoes", new BigDecimal("59.99"), 3, null, null, null, null,
                null, null, null, null, description);
    }

    private static Product existing() {
        Product p = new Product("Trail shoe", "SH-1", "shoes", new BigDecimal("59.99"), 3);
        ReflectionTestUtils.setField(p, "id", 7L);
        return p;
    }

    @Test
    void theTextIsTidiedButTheOwnersParagraphsStay() {
        Product p = existing();
        p.changeDescription("  Light and grippy.  \r\n\r\n\r\n\r\nGood for wet rocks.\t \nRuns half a size small.\r\n ");
        assertThat(p.getDescription()).isEqualTo("Light and grippy.\n\nGood for wet rocks.\nRuns half a size small.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\n\t"})
    void aBlankDescriptionIsNone(String text) {
        Product p = existing();
        p.changeDescription("Something");
        p.changeDescription(text);
        assertThat(p.getDescription()).isNull();
    }

    @Test
    void aNewProductKeepsItsDescriptionAndTheAuditTrailRecordsIt() {
        when(repo.save(any(Product.class))).thenAnswer(call -> {
            Product saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });

        Product created = service.create(request("Light and grippy.\n\nGood for wet rocks."));

        assertThat(created.getDescription()).isEqualTo("Light and grippy.\n\nGood for wet rocks.");
        verify(events).publishEvent(org.mockito.ArgumentMatchers.<AuditEvent>argThat(e -> e.changes().stream().anyMatch(c ->
                c.field().equals("description") && "Light and grippy.\n\nGood for wet rocks.".equals(c.newValue()))));
    }

    @Test
    void anUpdateChangesOrRemovesTheDescriptionAndIsAudited() {
        Product p = existing();
        p.changeDescription("Old words");
        when(repo.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(p));
        when(repo.findBySku("SH-1")).thenReturn(Optional.of(p));
        when(repo.save(p)).thenReturn(p);

        service.update(7L, request("New words"));
        assertThat(p.getDescription()).isEqualTo("New words");
        verify(events).publishEvent(AuditEvent.of(List.of(
                new AuditChange("PRODUCT", 7L, "PRODUCT_UPDATED", "description", "Old words", "New words"))));

        service.update(7L, request(null));                         // PUT replaces: no description = none
        assertThat(p.getDescription()).isNull();
    }
}
