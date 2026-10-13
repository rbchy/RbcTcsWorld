package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository repo;

    @InjectMocks
    ProductService service;

    private static Product product(long id, String sku) {
        Product p = new Product("Old name", sku, "old", new BigDecimal("10.00"), 5);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void updateChangesEveryField_regressionForStockOnlyBug() {
        Product existing = product(1L, "SKU-1");
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(existing));
        when(repo.findBySku("SKU-NEW")).thenReturn(Optional.empty());
        when(repo.save(existing)).thenReturn(existing);

        Product updated = service.update(1L,
                new ProductRequest("New name", "SKU-NEW", "books", new BigDecimal("12.50"), 9));

        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getSku()).isEqualTo("SKU-NEW");
        assertThat(updated.getCategory()).isEqualTo("books");
        assertThat(updated.getPrice()).isEqualByComparingTo("12.50");
        assertThat(updated.getStock()).isEqualTo(9);
    }

    @Test
    void updateRejectsSkuUsedByAnotherProduct() {
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product(1L, "SKU-1")));
        when(repo.findBySku("SKU-2")).thenReturn(Optional.of(product(2L, "SKU-2")));

        assertThatThrownBy(() -> service.update(1L,
                new ProductRequest("n", "SKU-2", null, BigDecimal.ONE, 1)))
                .isInstanceOf(ConflictException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void createRejectsDuplicateSku() {
        when(repo.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new ProductRequest("n", "SKU-1", null, BigDecimal.ONE, 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("SKU-1");
    }

    @Test
    void getInactiveOrMissingProductIsNotFound() {
        when(repo.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteIsSoftDelete() {
        Product existing = product(1L, "SKU-1");
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        assertThat(existing.isActive()).isFalse();
        verify(repo).save(existing);
    }

    // ---------- DEF-007 pagination ----------

    @ParameterizedTest(name = "page={0} size={1} sort={2}")
    @CsvSource({"-1, 20, id", "0, 0, id", "0, 101, id", "0, 20, stock"})
    void invalidPageSizeOrSortIs400(int page, int size, String sort) {
        assertThatThrownBy(() -> service.page(null, page, size, sort)).isInstanceOf(BusinessRuleException.class);
        verify(repo, never()).findByActiveTrue(any());
    }

    @Test
    void requestedPageAndStableSortReachTheDatabase() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        Page<Product> one = new PageImpl<>(List.of(product(1L, "A")));
        when(repo.findByActiveTrue(captor.capture())).thenReturn(one);

        service.page(" ", 2, 100, "PRICE_DESC");

        Pageable p = captor.getValue();
        assertThat(p.getPageNumber()).isEqualTo(2);
        assertThat(p.getPageSize()).isEqualTo(100);
        assertThat(p.getSort()).containsExactly(Sort.Order.desc("price"), Sort.Order.asc("id"));
    }

    @Test
    void searchUsesTheNameQueryAndDefaultsToIdOrder() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(repo.findByNameContainingIgnoreCaseAndActiveTrue(org.mockito.ArgumentMatchers.eq("mouse"), captor.capture()))
                .thenReturn(Page.empty());

        service.page("  mouse ", 0, 20, null);

        assertThat(captor.getValue().getSort()).containsExactly(Sort.Order.asc("id"));
    }

    @Test
    void nameSortIgnoresCaseAndEndsWithIdSoPagesAreStable() {
        Sort sort = ProductService.sortOf("name");
        assertThat(sort.getOrderFor("name").isIgnoreCase()).isTrue();
        assertThat(sort.getOrderFor("id")).isNotNull();
    }

    @Test
    void smallestPageSizeAndBlankSortAreAcceptedAndThePageIsReturned() {  // added after PIT: size=1 and a blank sort were untested
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        Page<Product> one = new PageImpl<>(List.of(product(1L, "A")));
        when(repo.findByActiveTrue(captor.capture())).thenReturn(one);

        assertThat(service.page(null, 0, 1, "   ")).isSameAs(one);
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
        assertThat(captor.getValue().getSort()).containsExactly(Sort.Order.asc("id"));
    }

    @Test
    void productMayKeepItsOwnSkuOnUpdate() {  // added after PIT: "SKU used by ANOTHER product" was only tested from one side
        Product existing = product(1L, "SKU-1");
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(existing));
        when(repo.findBySku("SKU-1")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Product updated = service.update(1L, new ProductRequest("New name", " SKU-1 ", "tools", new BigDecimal("12.00"), 3));

        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getSku()).isEqualTo("SKU-1");
    }

    // ---------- product pictures ----------

    @Test
    void createKeepsThePicture() {
        when(repo.existsBySku("SKU-P")).thenReturn(false);
        when(repo.save(org.mockito.ArgumentMatchers.any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product p = service.create(new ProductRequest("Lamp", "SKU-P", "home", new BigDecimal("9.99"), 3,
                " https://cdn.example.com/lamp.png "));

        assertThat(p.getImageUrl()).isEqualTo("https://cdn.example.com/lamp.png");
    }

    @Test
    void updateReplacesOrRemovesThePicture() {
        Product existing = product(1L, "SKU-1");
        existing.changeImage("/products/old.svg");
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(existing));
        when(repo.findBySku("SKU-1")).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        service.update(1L, new ProductRequest("Old name", "SKU-1", "old", new BigDecimal("10.00"), 5, "/products/new.svg"));
        assertThat(existing.getImageUrl()).isEqualTo("/products/new.svg");

        service.update(1L, new ProductRequest("Old name", "SKU-1", "old", new BigDecimal("10.00"), 5));
        assertThat(existing.getImageUrl()).isNull();                  // PUT without a picture removes it
    }

    @Test
    void blankPictureMeansNone() {
        Product p = product(2L, "SKU-2");
        p.changeImage("   ");
        assertThat(p.getImageUrl()).isNull();
    }
}
