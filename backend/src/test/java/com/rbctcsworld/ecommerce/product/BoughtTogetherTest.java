package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Customers who bought this also bought": REQ-CAT-13. */
@ExtendWith(MockitoExtension.class)
class BoughtTogetherTest {

    @Mock ProductRepository repo;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @Mock com.rbctcsworld.ecommerce.inventory.InventoryService inventory;
    @InjectMocks ProductService service;

    private static Product p(long id, int stock, String group, String size) {
        Product p = new Product("P" + id, "S" + id, "home", BigDecimal.TEN, stock);
        ReflectionTestUtils.setField(p, "id", id);
        if (group != null) p.changeVariant(group, size, null);
        return p;
    }

    private static Object[] row(long productId, long orders) {
        return new Object[]{productId, orders};
    }

    @Test
    void mostFrequentFirstAndOnlyWhatCanBeBought() {
        Product source = p(1, 5, null, null);
        Product soldOut = p(3, 0, null, null);
        Product inactive = p(4, 5, null, null);
        inactive.setActive(false);
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(source));
        when(repo.boughtWith(anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(row(2, 9), row(3, 7), row(4, 5), row(5, 2), row(6, 1)));
        when(repo.findAllById(anyCollection())).thenReturn(List.of(p(5, 1, null, null), p(2, 2, null, null), soldOut, inactive, p(6, 4, null, null)));

        assertThat(service.boughtTogether(1, 3)).extracting(Product::getId).containsExactly(2L, 5L, 6L);   // 3 and 4 are skipped
        assertThat(service.boughtTogether(1, 2)).extracting(Product::getId).containsExactly(2L, 5L);
    }

    @Test
    void allSizesOfTheItemCountAsTheItemAndEachRecommendedItemAppearsOnce() {
        Product navyS = p(10, 3, "TEE", "S");
        when(repo.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(navyS));
        when(repo.variantsOf("TEE")).thenReturn(List.of(navyS, p(11, 3, "TEE", "M")));
        when(repo.boughtWith(anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(row(20, 5), row(21, 4), row(22, 3)));
        when(repo.findAllById(anyCollection())).thenReturn(List.of(p(20, 2, "CAP", "S"), p(21, 2, "CAP", "M"), p(22, 2, null, null)));

        List<Product> out = service.boughtTogether(10, 4);

        assertThat(out).extracting(Product::getId).containsExactly(20L, 22L);          // the second cap size is the same item
        @SuppressWarnings("unchecked") ArgumentCaptor<Collection<Long>> sources = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).boughtWith(sources.capture(), page.capture());
        assertThat(sources.getValue()).containsExactlyInAnyOrder(10L, 11L);            // both sizes of the T-shirt are the source
        assertThat(page.getValue().getPageSize()).isEqualTo(4 * 6 + 12);               // asks for extra: some are dropped
    }

    @Test
    void noHistoryMeansNoRecommendations() {
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(p(1, 5, null, null)));
        when(repo.boughtWith(anyCollection(), any(Pageable.class))).thenReturn(List.of());
        assertThat(service.boughtTogether(1, null)).isEmpty();
    }

    @Test
    void theDefaultIsFourAndTheLimitIsChecked() {
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(p(1, 5, null, null)));
        when(repo.boughtWith(anyCollection(), any(Pageable.class))).thenReturn(List.of(row(2, 1)));
        when(repo.findAllById(anyCollection())).thenReturn(List.of(p(2, 1, null, null)));
        assertThat(service.boughtTogether(1, null)).hasSize(1);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).boughtWith(anyCollection(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(4 * 6 + 12);

        assertThatThrownBy(() -> service.boughtTogether(1, 0)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("limit must be between 1 and 12");
        assertThatThrownBy(() -> service.boughtTogether(1, 13)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anUnknownOrInactiveProductIs404() {
        when(repo.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.boughtTogether(99, 4)).isInstanceOf(NotFoundException.class);
        verifyNoInteractionsWithTheOrders();
    }

    private void verifyNoInteractionsWithTheOrders() {
        verify(repo, org.mockito.Mockito.never()).boughtWith(anyCollection(), any(Pageable.class));
    }
}
