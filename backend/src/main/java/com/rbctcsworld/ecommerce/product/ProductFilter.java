package com.rbctcsworld.ecommerce.product;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Catalog filters (all optional):
 *   category   one or more, comma separated ("books,home"), case-insensitive
 *   minPrice / maxPrice   inclusive
 *   inStock    only products that can be bought now
 *   minRating  average rating at least this (0-5)
 *   colors / sizes        one or more (case-insensitive); only variants have them
 *   grouped    one card per variant group (the storefront; the store admin sees every variant)
 * A product with variants is found when ANY of its variants passes every filter; its card is the first such variant.
 */
public record ProductFilter(String category, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock, Boolean grouped,
                            BigDecimal minRating, List<String> colors, List<String> sizes) {

    public static final ProductFilter NONE = new ProductFilter(null, null, null, null, null);

    public ProductFilter(String category, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {
        this(category, minPrice, maxPrice, inStock, null);
    }

    public ProductFilter(String category, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock, Boolean grouped) {
        this(category, minPrice, maxPrice, inStock, grouped, null, null, null);
    }

    /** The categories asked for, lower case, without blanks or duplicates. */
    public List<String> categories() {
        return category == null ? List.of() : clean(Arrays.asList(category.split(",")));
    }

    public List<String> colorList() {
        return clean(colors);
    }

    public List<String> sizeList() {
        return clean(sizes);
    }

    static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).flatMap(v -> Arrays.stream(v.split(",")))
                .map(v -> v.trim().toLowerCase(Locale.ROOT)).filter(v -> !v.isEmpty()).distinct().toList();
    }

    /** No filter at all: the plain (faster) catalog query is used. */
    public boolean isEmpty() {
        return categories().isEmpty() && minPrice == null && maxPrice == null && !Boolean.TRUE.equals(inStock)
                && !Boolean.TRUE.equals(grouped) && minRating == null && colorList().isEmpty() && sizeList().isEmpty();
    }

    /** The same filter without one facet (for counting that facet's options: "disjunctive" facets). */
    public ProductFilter without(String facet) {
        return switch (facet) {
            case "category" -> new ProductFilter(null, minPrice, maxPrice, inStock, grouped, minRating, colors, sizes);
            case "price" -> new ProductFilter(category, null, null, inStock, grouped, minRating, colors, sizes);
            case "inStock" -> new ProductFilter(category, minPrice, maxPrice, null, grouped, minRating, colors, sizes);
            case "rating" -> new ProductFilter(category, minPrice, maxPrice, inStock, grouped, null, colors, sizes);
            case "color" -> new ProductFilter(category, minPrice, maxPrice, inStock, grouped, minRating, null, sizes);
            case "size" -> new ProductFilter(category, minPrice, maxPrice, inStock, grouped, minRating, colors, null);
            default -> throw new IllegalArgumentException("Unknown facet " + facet);
        };
    }

    /** Does this one product (one variant) pass every filter? The in-memory twin of ProductService.spec. */
    public boolean matches(Product p) {
        List<String> cats = categories();
        if (!cats.isEmpty() && (p.getCategory() == null || !cats.contains(p.getCategory().trim().toLowerCase(Locale.ROOT)))) return false;
        if (minPrice != null && p.getPrice().compareTo(minPrice) < 0) return false;
        if (maxPrice != null && p.getPrice().compareTo(maxPrice) > 0) return false;
        if (Boolean.TRUE.equals(inStock) && p.getStock() <= 0) return false;
        if (minRating != null && p.getRatingAverage().compareTo(minRating) < 0) return false;
        List<String> c = colorList();
        if (!c.isEmpty() && (p.getColor() == null || !c.contains(p.getColor().toLowerCase(Locale.ROOT)))) return false;
        List<String> s = sizeList();
        return s.isEmpty() || (p.getSize() != null && s.contains(p.getSize().toLowerCase(Locale.ROOT)));
    }
}
