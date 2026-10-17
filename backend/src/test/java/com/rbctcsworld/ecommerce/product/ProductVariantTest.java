package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Product variants (size / colour): REQ-CAT-07..10. */
@ExtendWith(MockitoExtension.class)
class ProductVariantTest {

    @Mock ProductRepository repo;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @Mock com.rbctcsworld.ecommerce.inventory.InventoryService inventory;
    @InjectMocks ProductService service;

    private static Product tee(long id, String size, String color, String price, int stock) {
        Product p = new Product("Classic Tee", "TEE-" + id, "clothing", new BigDecimal(price), stock);
        p.changeVariant("TEE", size, color);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static long nextId = 100;

    /** Like the database: a saved product gets an id (the inventory needs it for the first stock). */
    private static Product withId(org.mockito.invocation.InvocationOnMock call) {
        Product p = call.getArgument(0);
        if (p.getId() == null) ReflectionTestUtils.setField(p, "id", nextId++);
        return p;
    }

    private static ProductRequest request(String sku, String group, String size, String color) {
        return new ProductRequest("Classic Tee", sku, "clothing", new BigDecimal("19.99"), 5, null, group, size, color);
    }

    // ------------------------------------------------------------- the variant fields and their rules

    @Test
    void aNewVariantIsSavedWithItsGroupInUpperCaseAndTidySizeAndColour() {
        when(repo.existsBySku("TEE-9")).thenReturn(false);
        when(repo.variantsOf("TEE-CLASSIC")).thenReturn(List.of(tee(1, "S", "Navy", "19.99", 3)));
        when(repo.save(any(Product.class))).thenAnswer(a -> { Product p = a.getArgument(0); ReflectionTestUtils.setField(p, "id", 9L); return p; });

        Product saved = service.create(request("TEE-9", "  tee-classic ", " M ", "  Heather   Grey "));

        assertThat(saved.getVariantGroup()).isEqualTo("TEE-CLASSIC");
        assertThat(saved.getSize()).isEqualTo("M");
        assertThat(saved.getColor()).isEqualTo("Heather Grey");
        assertThat(saved.displayName()).isEqualTo("Classic Tee (Heather Grey, M)");
    }

    @Test
    void aProductWithoutVariantFieldsStaysASingleProduct() {
        when(repo.existsBySku("ONE")).thenReturn(false);
        when(repo.save(any(Product.class))).thenAnswer(ProductVariantTest::withId);
        Product saved = service.create(request("ONE", " ", "", null));
        assertThat(saved.getVariantGroup()).isNull();
        assertThat(saved.getSize()).isNull();
        assertThat(saved.getColor()).isNull();
        assertThat(saved.displayName()).isEqualTo("Classic Tee");
        verify(repo, never()).variantsOf(any());
    }

    @Test
    void aSizeOrColourNeedsAGroupAndAGroupNeedsASizeOrColour() {
        when(repo.existsBySku(any())).thenReturn(false);
        assertThatThrownBy(() -> service.create(request("A", null, "M", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("A size or colour needs a variant group");
        assertThatThrownBy(() -> service.create(request("B", "", null, "Navy")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("A size or colour needs a variant group");
        assertThatThrownBy(() -> service.create(request("C", "TEE", " ", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("A variant needs a size, a colour or both");
        verify(repo, never()).save(any());
    }

    @Test
    void onlyASizeOrOnlyAColourIsEnough() {
        when(repo.existsBySku(any())).thenReturn(false);
        when(repo.variantsOf("MUG")).thenReturn(List.of());
        when(repo.save(any(Product.class))).thenAnswer(ProductVariantTest::withId);
        Product sizeOnly = service.create(request("MUG-12", "mug", "12 oz", null));
        Product colourOnly = service.create(request("MUG-RED", "mug", null, "Red"));
        assertThat(sizeOnly.displayName()).isEqualTo("Classic Tee (12 oz)");
        assertThat(colourOnly.displayName()).isEqualTo("Classic Tee (Red)");
    }

    @Test
    void twoProductsOfOneGroupCannotHaveTheSameSizeAndColourIgnoringCase() {
        when(repo.existsBySku("TEE-9")).thenReturn(false);
        when(repo.variantsOf("TEE")).thenReturn(List.of(tee(1, "M", "Navy", "19.99", 3)));
        assertThatThrownBy(() -> service.create(request("TEE-9", "TEE", "m", "NAVY")))
                .isInstanceOf(ConflictException.class).hasMessage("Variant group TEE already has NAVY / m (SKU TEE-1)");
        verify(repo, never()).save(any());
    }

    @Test
    void aDifferentSizeOrColourInTheSameGroupIsFine() {
        when(repo.existsBySku(any())).thenReturn(false);
        when(repo.variantsOf("TEE")).thenReturn(List.of(tee(1, "M", "Navy", "19.99", 3), tee(2, null, "Red", "19.99", 3),
                tee(3, "L", null, "19.99", 3)));
        when(repo.save(any(Product.class))).thenAnswer(ProductVariantTest::withId);
        assertThat(service.create(request("X1", "TEE", "L", "Navy")).getSize()).isEqualTo("L");      // same colour, other size
        assertThat(service.create(request("X2", "TEE", "M", "Red")).getColor()).isEqualTo("Red");    // same size, other colour
        assertThat(service.create(request("X3", "TEE", "S", null)).getSize()).isEqualTo("S");        // size only, unused size
        assertThatThrownBy(() -> service.create(request("X4", "TEE", null, "red"))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.create(request("X5", "TEE", "l", null))).isInstanceOf(ConflictException.class);
    }

    @Test
    void anUpdateMayKeepItsOwnSizeAndColourAndAPutWithoutThemEndsTheVariant() {
        Product me = tee(1, "M", "Navy", "19.99", 3);
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(me));
        when(repo.findBySku("TEE-1")).thenReturn(Optional.of(me));
        when(repo.variantsOf("TEE")).thenReturn(List.of(me, tee(2, "L", "Navy", "19.99", 3)));
        when(repo.save(me)).thenReturn(me);

        assertThat(service.update(1L, request("TEE-1", "TEE", "M", "Navy")).getSize()).isEqualTo("M");
        assertThatThrownBy(() -> service.update(1L, request("TEE-1", "TEE", "L", "Navy"))).isInstanceOf(ConflictException.class);
        assertThat(me.getSize()).isEqualTo("M");                         // a refused update changes nothing

        Product single = service.update(1L, request("TEE-1", null, null, null));
        assertThat(single.getVariantGroup()).isNull();
        assertThat(single.getSize()).isNull();
        assertThat(single.getColor()).isNull();
    }

    @Test
    void theVariantFieldsAreAudited() {
        when(repo.existsBySku("TEE-9")).thenReturn(false);
        when(repo.variantsOf("TEE")).thenReturn(List.of());
        when(repo.save(any(Product.class))).thenAnswer(a -> { Product p = a.getArgument(0); ReflectionTestUtils.setField(p, "id", 9L); return p; });
        service.create(request("TEE-9", "TEE", "M", "Navy"));
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        String audit = event.getValue().toString();
        assertThat(audit).contains("variantGroup").contains("TEE").contains("size").contains("color").contains("Navy");
    }

    @Test
    void withoutAGroupTheEntityDropsSizeAndColour() {
        Product p = new Product("Mug", "M-1", "home", BigDecimal.ONE, 1);
        p.changeVariant(null, "L", "Red");
        assertThat(p.getSize()).isNull();
        assertThat(p.getColor()).isNull();
        assertThat(p.displayName()).isEqualTo("Mug");
        p.changeVariant("G", null, " ");
        assertThat(p.displayName()).isEqualTo("Mug");                    // blank colour adds nothing
    }

    // ------------------------------------------------------------- reading variants

    @Test
    void theVariantsOfAProductAreItsWholeGroupOrJustItself() {
        Product m = tee(1, "M", "Navy", "19.99", 3);
        Product l = tee(2, "L", "Navy", "19.99", 0);
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(m));
        when(repo.variantsOf("TEE")).thenReturn(List.of(m, l));
        assertThat(service.variants(1L)).containsExactly(m, l);

        Product mug = new Product("Mug", "MUG", "home", BigDecimal.TEN, 1);
        ReflectionTestUtils.setField(mug, "id", 5L);
        when(repo.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(mug));
        assertThat(service.variants(5L)).containsExactly(mug);

        assertThatThrownBy(() -> service.variants(7L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void summariesListOptionsColoursSizesInClothingOrderPriceRangeAndUnits() {
        when(repo.variantsOfAll(any())).thenReturn(List.of(
                tee(1, "XL", "Navy", "21.99", 0), tee(2, "S", "Navy", "19.99", 12), tee(3, "M", "Grey", "19.99", 4),
                tee(4, "S", "Grey", "19.99", 1)));
        List<VariantSummary> s = service.variantSummaries(List.of(" tee ", "TEE", "NOPE", " "));

        assertThat(s).hasSize(1);                                         // duplicates merged; an unknown group is left out
        VariantSummary tee = s.get(0);
        assertThat(tee.group()).isEqualTo("TEE");
        assertThat(tee.options()).isEqualTo(4);
        assertThat(tee.colors()).containsExactly("Navy", "Grey");
        assertThat(tee.sizes()).containsExactly("S", "M", "XL");
        assertThat(tee.minPrice()).isEqualByComparingTo("19.99");
        assertThat(tee.maxPrice()).isEqualByComparingTo("21.99");
        assertThat(tee.stock()).isEqualTo(17);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> asked = ArgumentCaptor.forClass(Collection.class);
        verify(repo).variantsOfAll(asked.capture());
        assertThat(asked.getValue()).containsExactly("TEE", "NOPE");
    }

    @Test
    void noGroupsAskedMeansNoQueryAndAtMostAHundredAtATime() {
        assertThat(service.variantSummaries(null)).isEmpty();
        assertThat(service.variantSummaries(List.of(" ", ""))).isEmpty();
        verify(repo, never()).variantsOfAll(any());
        List<String> many = java.util.stream.IntStream.rangeClosed(1, 101).mapToObj(i -> "G" + i).toList();
        assertThatThrownBy(() -> service.variantSummaries(many)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("at most 100 groups at a time");
        assertThat(service.variantSummaries(many.subList(0, 100))).isEmpty();
    }

    @Test
    void sizesSortClothingFirstThenNumbersThenTheRest() {
        List<String> sorted = java.util.stream.Stream.of("XL", "one size", "10.5", "s", "XXS", "8", "M", "12 oz", "xxxl", "Kids")
                .sorted(VariantSummary.SIZES).toList();
        assertThat(sorted).containsExactly("XXS", "s", "M", "XL", "xxxl", "8", "10.5", "12 oz", "Kids", "one size");
    }

    @Test
    void otherSizesAndColoursOfTheSameItemAreNotSimilarProducts() {
        Product m = tee(1, "M", "Navy", "19.99", 3);
        Product l = tee(2, "L", "Navy", "19.99", 3);
        Product hoodie = new Product("Hoodie", "HOOD", "clothing", new BigDecimal("39.00"), 3);
        ReflectionTestUtils.setField(hoodie, "id", 3L);
        Product sock = new Product("Socks", "SOCK", "clothing", new BigDecimal("5.00"), 3);
        ReflectionTestUtils.setField(sock, "id", 4L);
        when(repo.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(m));
        when(repo.variantsOf("TEE")).thenReturn(List.of(m, l));
        when(repo.similarInCategory(eq(1L), eq("clothing"), eq(new BigDecimal("19.99")), any(Pageable.class)))
                .thenReturn(List.of(l, hoodie, sock));

        assertThat(service.similar(1L, 1)).containsExactly(hoodie);       // the sibling is skipped, the limit still holds
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).similarInCategory(eq(1L), eq("clothing"), eq(new BigDecimal("19.99")), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(3);          // asks for the limit + the 2 variants
    }

    @Test
    void theStorefrontsGroupedCatalogUsesTheFilteredQuery() {
        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        assertThat(new ProductFilter(null, null, null, null, true).isEmpty()).isFalse();
        assertThat(new ProductFilter(null, null, null, null, false).isEmpty()).isTrue();
        service.page(null, 0, 20, "id", new ProductFilter(null, null, null, null, true));
        verify(repo).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class));
        verify(repo, never()).findByActiveTrue(any());
    }
}
