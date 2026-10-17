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

    @Mock
    org.springframework.context.ApplicationEventPublisher events;

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

    private static com.rbctcsworld.ecommerce.audit.AuditChange change(long id, String action, String field, String from, String to) {
        return new com.rbctcsworld.ecommerce.audit.AuditChange("PRODUCT", id, action, field, from, to);
    }

    @Test
    void anUpdateIsAuditedFieldByFieldWithOnlyWhatChanged() {
        Product existing = product(1L, "SKU-1");
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(existing));
        when(repo.findBySku("SKU-1")).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        service.update(1L, new ProductRequest("Old name", "SKU-1", "old", new BigDecimal("12.50"), 9));

        verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.of(java.util.List.of(
                change(1L, "PRODUCT_UPDATED", "price", "10.00", "12.50"),
                change(1L, "PRODUCT_UPDATED", "stock", "5", "9"))));
    }

    @Test
    void anUpdateThatChangesNothingHasNothingToAudit() {
        Product existing = product(1L, "SKU-1");
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(existing));
        when(repo.findBySku("SKU-1")).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        service.update(1L, new ProductRequest("Old name", "SKU-1", "old", new BigDecimal("10.0"), 5));   // 10.0 == 10.00

        verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.of(java.util.List.of()));
    }

    @Test
    void aNewProductIsAuditedWithEveryValue() {
        when(repo.existsBySku("SKU-9")).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 9L);
            return p;
        });

        service.create(new ProductRequest("Lamp", "SKU-9", "home", new BigDecimal("20.00"), 3));

        verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.of(java.util.List.of(
                change(9L, "PRODUCT_CREATED", "name", null, "Lamp"),
                change(9L, "PRODUCT_CREATED", "sku", null, "SKU-9"),
                change(9L, "PRODUCT_CREATED", "category", null, "home"),
                change(9L, "PRODUCT_CREATED", "price", null, "20.00"),
                change(9L, "PRODUCT_CREATED", "stock", null, "3"),
                change(9L, "PRODUCT_CREATED", "active", null, "true"))));
    }

    @Test
    void aDeletedProductIsAuditedAsNoLongerActive() {
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product(1L, "SKU-1")));

        service.delete(1L);

        verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.of(java.util.List.of(
                change(1L, "PRODUCT_DELETED", "active", "true", "false"))));
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
        when(repo.save(org.mockito.ArgumentMatchers.any(Product.class))).thenAnswer(inv -> {
            Product saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 11L);   // the database gives every new product an id
            return saved;
        });

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

    // ---------------------------------------------------------------- advanced search

    @Test
    void withoutExtraFiltersTheCatalogQueryStaysTheSame() {
        when(repo.findByActiveTrue(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        service.page(null, 0, 20, "id", new ProductFilter(" ", null, null, false));
        service.page(null, 0, 20, "id", null);
        verify(repo, org.mockito.Mockito.times(2)).findByActiveTrue(any(org.springframework.data.domain.Pageable.class));
        verify(repo, org.mockito.Mockito.never()).findAll(
                any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void anyExtraFilterUsesTheFilteredQueryWithTheSamePaging() {
        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(org.springframework.data.domain.Page.empty());
        for (ProductFilter f : List.of(new ProductFilter("home", null, null, null),
                new ProductFilter(null, new BigDecimal("1"), null, null),
                new ProductFilter(null, null, new BigDecimal("9"), null),
                new ProductFilter(null, null, null, true))) {
            service.page("lamp", 2, 5, "price_desc", f);
        }
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> paging =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(repo, org.mockito.Mockito.times(4)).findAll(
                any(org.springframework.data.jpa.domain.Specification.class), paging.capture());
        assertThat(paging.getValue().getPageNumber()).isEqualTo(2);
        assertThat(paging.getValue().getPageSize()).isEqualTo(5);
        assertThat(paging.getValue().getSort()).isEqualTo(ProductService.sortOf("price_desc"));
    }

    @Test
    void thePriceRangeMustMakeSense() {
        assertThatThrownBy(() -> service.page(null, 0, 20, "id", new ProductFilter(null, new BigDecimal("-0.01"), null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("minPrice cannot be negative");
        assertThatThrownBy(() -> service.page(null, 0, 20, "id", new ProductFilter(null, null, new BigDecimal("-1"), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("maxPrice cannot be negative");
        assertThatThrownBy(() -> service.page(null, 0, 20, "id",
                new ProductFilter(null, new BigDecimal("10"), new BigDecimal("9.99"), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("maxPrice must not be below minPrice");
        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(org.springframework.data.domain.Page.empty());
        assertThat(service.page(null, 0, 20, "id",
                new ProductFilter(null, new BigDecimal("0"), new BigDecimal("0"), null)).getContent()).isEmpty();
    }

    @Test
    void theFilterKnowsWhenItIsEmpty() {
        assertThat(ProductFilter.NONE.isEmpty()).isTrue();
        assertThat(new ProductFilter("", null, null, Boolean.FALSE).isEmpty()).isTrue();
        assertThat(new ProductFilter("x", null, null, null).isEmpty()).isFalse();
        assertThat(new ProductFilter(null, BigDecimal.ONE, null, null).isEmpty()).isFalse();
        assertThat(new ProductFilter(null, null, BigDecimal.ONE, null).isEmpty()).isFalse();
        assertThat(new ProductFilter(null, null, null, Boolean.TRUE).isEmpty()).isFalse();
    }

    @Test
    void categoriesComeFromTheProductsOnSale() {
        when(repo.activeCategories()).thenReturn(List.of("books", "home"));
        assertThat(service.categories()).containsExactly("books", "home");
    }

    // ---------------------------------------------------------------- similar products

    private Product onSale(long id, String category, String price) {
        Product p = new Product("P" + id, "SKU-" + id, category, new BigDecimal(price), 5);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void similarProductsComeFromTheSameCategoryNearestPriceFirst() {
        Product lamp = onSale(1L, "home", "20.00");
        Product mug = onSale(2L, "home", "18.00");
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(lamp));
        when(repo.similarInCategory(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("home"),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("20.00")), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(mug));

        assertThat(service.similar(1L, null)).containsExactly(mug);
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> page =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(repo).similarInCategory(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("home"),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("20.00")), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(4);
        verify(repo, org.mockito.Mockito.never()).similarByPrice(any(), any(), any());
    }

    @Test
    void withoutACategorySimilarMeansTheClosestPrice() {
        for (String none : new String[]{null, " "}) {
            Product p = onSale(1L, none, "20.00");
            when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(p));
            when(repo.similarByPrice(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(new BigDecimal("20.00")),
                    any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of());
            assertThat(service.similar(1L, 12)).isEmpty();
        }
        verify(repo, org.mockito.Mockito.never()).similarInCategory(any(), any(), any(), any());
    }

    @Test
    void theNumberOfSimilarProductsIsLimitedAndTheProductMustBeOnSale() {
        assertThatThrownBy(() -> service.similar(1L, 0)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("limit must be between 1 and 12");
        assertThatThrownBy(() -> service.similar(1L, 13)).isInstanceOf(BusinessRuleException.class);
        when(repo.findByIdAndActiveTrue(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.similar(9L, 1)).isInstanceOf(NotFoundException.class);
    }
}
