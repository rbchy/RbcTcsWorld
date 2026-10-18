package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Faceted search: REQ-CAT-11..12. */
@ExtendWith(MockitoExtension.class)
class FacetSearchTest {

    @Mock ProductRepository repo;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @Mock com.rbctcsworld.ecommerce.inventory.InventoryService inventory;
    @InjectMocks ProductService service;

    private static long ids = 1;

    private static Product p(String category, String price, int stock, String rating, String group, String size, String color) {
        Product p = new Product("P", "S" + ids, category, new BigDecimal(price), stock);
        ReflectionTestUtils.setField(p, "id", ids++);
        p.applyRating(new BigDecimal(rating), 1);
        p.changeVariant(group, size, color);
        return p;
    }

    /** A small catalog: 2 books, 1 home item, and a T-shirt with 4 variants (one item). */
    private static List<Product> catalog() {
        return List.of(
                p("books", "10.00", 5, "4.5", null, null, null),
                p("Books", "60.00", 0, "3.0", null, null, null),
                p("home", "25.00", 2, "4.0", null, null, null),
                p("clothing", "19.99", 3, "4.2", "TEE", "S", "Navy"),
                p("clothing", "19.99", 0, "4.2", "TEE", "M", "Navy"),
                p("clothing", "21.99", 4, "4.2", "TEE", "XL", "Grey"),
                p("clothing", "19.99", 1, "4.2", "TEE", "m", "grey"));
    }

    private static long count(List<Facets.Option> options, String value) {
        return options.stream().filter(o -> o.value().equals(value)).mapToLong(Facets.Option::count).sum();
    }

    @Test
    void withoutFiltersEveryItemIsCountedOnceWhateverItsVariants() {
        Facets f = Facets.count(catalog(), ProductFilter.NONE);
        assertThat(f.total()).isEqualTo(4);                               // 3 single products + 1 T-shirt
        assertThat(f.categories()).extracting(Facets.Option::value).containsExactly("books", "clothing", "home");
        assertThat(count(f.categories(), "books")).isEqualTo(2);         // "Books" and "books" are one category
        assertThat(count(f.categories(), "clothing")).isEqualTo(1);
        assertThat(f.inStock()).isEqualTo(3);                             // the 60.00 book has none left
        assertThat(f.colors()).extracting(Facets.Option::value).containsExactly("Grey", "Navy");
        assertThat(f.sizes()).extracting(Facets.Option::value).containsExactly("S", "M", "XL");
        assertThat(count(f.sizes(), "M")).isEqualTo(1);                   // "M" and "m": one size, one item
    }

    @Test
    void priceBandsAreInclusiveAndCountItems() {
        Facets f = Facets.count(catalog(), ProductFilter.NONE);
        assertThat(f.prices()).extracting(Facets.PriceBand::key)
                .containsExactly("under-25", "25-50", "50-100", "100-200", "200-up");
        assertThat(f.prices()).extracting(Facets.PriceBand::count).containsExactly(2L, 1L, 1L, 0L, 0L);
        assertThat(f.prices().get(0).max()).isEqualByComparingTo("24.99");
        assertThat(f.prices().get(4).max()).isNull();
    }

    @Test
    void ratingsCountItemsWithAtLeastThatAverage() {
        Facets f = Facets.count(catalog(), ProductFilter.NONE);
        assertThat(f.ratings()).extracting(Facets.RatingOption::min).containsExactly(4, 3, 2, 1);
        assertThat(f.ratings()).extracting(Facets.RatingOption::count).containsExactly(3L, 4L, 4L, 4L);
    }

    @Test
    void aFacetIgnoresItsOwnSelectionButEveryOtherFilterApplies() {
        ProductFilter books = new ProductFilter("books", null, null, null, true);
        Facets f = Facets.count(catalog(), books);
        assertThat(f.total()).isEqualTo(2);
        assertThat(count(f.categories(), "home")).isEqualTo(1);           // still offered, with its count
        assertThat(f.inStock()).isEqualTo(1);                             // only one book can be bought
        assertThat(f.colors()).isEmpty();                                 // no book has a colour

        ProductFilter grey = new ProductFilter(null, null, null, true, true, null, List.of("GREY"), null);
        Facets g = Facets.count(catalog(), grey);
        assertThat(g.total()).isEqualTo(1);
        assertThat(count(g.colors(), "Navy")).isEqualTo(1);               // the colour facet ignores "grey"
        assertThat(g.sizes()).extracting(Facets.Option::value).containsExactly("m", "XL");   // grey sizes; the first spelling seen is shown ("m")
        assertThat(count(g.categories(), "books")).isZero();               // books have no colour: not counted
    }

    @Test
    void theFilterMatchesOneProductLikeTheDatabaseQuery() {
        Product navyS = catalog().get(3);
        assertThat(new ProductFilter("Clothing, home", null, null, null).matches(navyS)).isTrue();
        assertThat(new ProductFilter("books", null, null, null).matches(navyS)).isFalse();
        assertThat(new ProductFilter(null, new BigDecimal("19.99"), new BigDecimal("19.99"), null).matches(navyS)).isTrue();
        assertThat(new ProductFilter(null, new BigDecimal("20"), null, null).matches(navyS)).isFalse();
        assertThat(new ProductFilter(null, null, new BigDecimal("19.98"), null).matches(navyS)).isFalse();
        assertThat(new ProductFilter(null, null, null, null, null, new BigDecimal("4.2"), null, null).matches(navyS)).isTrue();
        assertThat(new ProductFilter(null, null, null, null, null, new BigDecimal("4.3"), null, null).matches(navyS)).isFalse();
        assertThat(new ProductFilter(null, null, null, null, null, null, List.of("navy,red"), List.of(" s ")).matches(navyS)).isTrue();
        assertThat(new ProductFilter(null, null, null, null, null, null, null, List.of("L")).matches(navyS)).isFalse();
        Product book = catalog().get(0);
        assertThat(new ProductFilter(null, null, null, null, null, null, List.of("Navy"), null).matches(book)).isFalse();
        assertThat(new ProductFilter(null, null, null, null, null, null, null, List.of("M")).matches(book)).isFalse();
        Product noCategory = p(null, "5.00", 0, "0", null, null, null);
        assertThat(new ProductFilter("books", null, null, null).matches(noCategory)).isFalse();
        assertThat(new ProductFilter(null, null, null, true).matches(noCategory)).isFalse();
        assertThat(ProductFilter.NONE.matches(noCategory)).isTrue();
    }

    @Test
    void emptyOrRepeatedValuesAreIgnored() {
        ProductFilter f = new ProductFilter(" books, ,BOOKS,home ", null, null, null, null, null, java.util.Arrays.asList("Navy", null, ""), List.of());
        assertThat(f.categories()).containsExactly("books", "home");
        assertThat(f.colorList()).containsExactly("navy");
        assertThat(f.sizeList()).isEmpty();
        assertThat(new ProductFilter(null, null, null, null, null, BigDecimal.ONE, null, null).isEmpty()).isFalse();
        assertThat(new ProductFilter(null, null, null, null, null, null, List.of("x"), null).isEmpty()).isFalse();
        assertThat(new ProductFilter(null, null, null, null, null, null, null, List.of("x")).isEmpty()).isFalse();
        assertThatThrownBy(() -> ProductFilter.NONE.without("brand")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutDropsExactlyOneFacet() {
        ProductFilter all = new ProductFilter("books", BigDecimal.ONE, BigDecimal.TEN, true, true, BigDecimal.ONE, List.of("Red"), List.of("M"));
        assertThat(all.without("category").category()).isNull();
        assertThat(all.without("price").minPrice()).isNull();
        assertThat(all.without("price").maxPrice()).isNull();
        assertThat(all.without("inStock").inStock()).isNull();
        assertThat(all.without("rating").minRating()).isNull();
        assertThat(all.without("color").colors()).isNull();
        assertThat(all.without("size").sizes()).isNull();
        assertThat(all.without("size").colors()).containsExactly("Red");
        assertThat(all.without("color").grouped()).isTrue();
    }

    @Test
    void theFacetsComeFromTheProductsMatchingTheSearch() {
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(catalog()));
        Facets f = service.facets("tee", new ProductFilter(null, null, null, null, true, new BigDecimal("4"), null, null));
        assertThat(f.total()).isEqualTo(3);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(any(Specification.class), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(ProductService.MAX_FACET_PRODUCTS);
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(service.facets(null, null).total()).isEqualTo(4);
    }

    @Test
    void badFiltersAre400() {
        assertThatThrownBy(() -> service.facets(null, new ProductFilter(null, null, null, null, null, new BigDecimal("5.1"), null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("minRating must be between 0 and 5");
        assertThatThrownBy(() -> service.facets(null, new ProductFilter(null, null, null, null, null, new BigDecimal("-0.1"), null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("minRating must be between 0 and 5");
        List<String> many = java.util.stream.IntStream.rangeClosed(1, 21).mapToObj(i -> "c" + i).toList();
        assertThatThrownBy(() -> service.page(null, 0, 20, "id", new ProductFilter(String.join(",", many), null, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("at most 20 values per filter");
        assertThatThrownBy(() -> service.page(null, 0, 20, "id", new ProductFilter(null, null, null, null, null, null, many, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.page(null, 0, 20, "id", new ProductFilter(null, null, null, null, null, null, null, many)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.facets(null, new ProductFilter(null, new BigDecimal("-1"), null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("minPrice cannot be negative");
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.facets(null, new ProductFilter(null, null, null, null, null, new BigDecimal("5"), many.subList(0, 20), null)).total()).isZero();
        assertThat(service.facets(null, new ProductFilter(null, null, null, null, null, BigDecimal.ZERO, null, null)).total()).isZero();
    }
}
