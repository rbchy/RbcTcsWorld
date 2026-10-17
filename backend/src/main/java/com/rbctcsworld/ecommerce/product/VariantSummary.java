package com.rbctcsworld.ecommerce.product;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What a catalog card says about an item with variants: "8 options, 2 colours, sizes S-XL, $19.99-$21.99".
 * Colours in the order they were added; sizes in clothing order (XXS..XXXL), then numbers, then the rest.
 */
public record VariantSummary(String group, int options, List<String> colors, List<String> sizes,
                             BigDecimal minPrice, BigDecimal maxPrice, int stock) {

    static final List<String> SIZE_ORDER = List.of("XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL");

    static VariantSummary of(String group, List<Product> variants) {
        List<String> colors = variants.stream().map(Product::getColor).filter(Objects::nonNull).distinct().toList();
        List<String> sizes = variants.stream().map(Product::getSize).filter(Objects::nonNull).distinct()
                .sorted(SIZES).toList();
        BigDecimal min = variants.stream().map(Product::getPrice).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal max = variants.stream().map(Product::getPrice).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        int stock = variants.stream().mapToInt(Product::getStock).sum();
        return new VariantSummary(group, variants.size(), colors, sizes, min, max, stock);
    }

    /** Clothing sizes first in their natural order, then numeric sizes by value, then anything else A-Z. */
    static final Comparator<String> SIZES = Comparator
            .comparingInt((String s) -> rank(s))
            .thenComparingDouble(VariantSummary::number)
            .thenComparing(s -> s.toLowerCase(Locale.ROOT));

    private static int rank(String s) {
        int i = SIZE_ORDER.indexOf(s.trim().toUpperCase(Locale.ROOT));
        if (i >= 0) return i;
        return Double.isNaN(number(s)) ? 200 : 100;
    }

    private static double number(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)").matcher(s);
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }
}
