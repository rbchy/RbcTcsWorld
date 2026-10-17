package com.rbctcsworld.ecommerce.product;

import java.math.BigDecimal;

/**
 * Extra catalog filters (all optional): category (exact, case-insensitive), price range (inclusive),
 * and inStock = only products that can be bought now.
 */
public record ProductFilter(String category, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {

    public static final ProductFilter NONE = new ProductFilter(null, null, null, null);

    public boolean isEmpty() {
        return (category == null || category.isBlank()) && minPrice == null && maxPrice == null
                && !Boolean.TRUE.equals(inStock);
    }
}
