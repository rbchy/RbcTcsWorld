package com.rbctcsworld.ecommerce.product;

import java.math.BigDecimal;

/**
 * Extra catalog filters (all optional): category (exact, case-insensitive), price range (inclusive),
 * inStock = only products that can be bought now, grouped = one card per variant group (the storefront;
 * the store admin sees every variant).
 */
public record ProductFilter(String category, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock, Boolean grouped) {

    public static final ProductFilter NONE = new ProductFilter(null, null, null, null, null);

    public ProductFilter(String category, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {
        this(category, minPrice, maxPrice, inStock, null);
    }

    public boolean isEmpty() {
        return (category == null || category.isBlank()) && minPrice == null && maxPrice == null
                && !Boolean.TRUE.equals(inStock) && !Boolean.TRUE.equals(grouped);
    }
}
