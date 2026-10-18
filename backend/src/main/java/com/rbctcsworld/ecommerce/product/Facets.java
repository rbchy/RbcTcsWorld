package com.rbctcsworld.ecommerce.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The filter panel of the catalog: for every option, how many ITEMS (catalog cards) would be found.
 * "Disjunctive" facets like on Amazon: the counts of one facet ignore that facet's own selection, so ticking
 * "books" still shows how many "home" items there are; every other selected filter does apply.
 *
 *   total       items matching everything
 *   categories  [{value, count}] A-Z
 *   prices      fixed bands: under 25, 25-50, 50-100, 100-200, 200 and up ({key, min, max, count}; max inclusive)
 *   ratings     4, 3, 2, 1 stars and up
 *   inStock     items with at least one variant in stock
 *   colors / sizes  [{value, count}] (sizes in clothing order)
 */
public record Facets(long total, List<Option> categories, List<PriceBand> prices, List<RatingOption> ratings, long inStock,
                     List<Option> colors, List<Option> sizes) {

    public record Option(String value, long count) {}

    public record PriceBand(String key, BigDecimal min, BigDecimal max, long count) {}

    public record RatingOption(int min, long count) {}

    static final List<PriceBand> BANDS = List.of(
            band("under-25", "0", "24.99"), band("25-50", "25", "49.99"), band("50-100", "50", "99.99"),
            band("100-200", "100", "199.99"), new PriceBand("200-up", new BigDecimal("200"), null, 0));

    private static PriceBand band(String key, String min, String max) {
        return new PriceBand(key, new BigDecimal(min), new BigDecimal(max), 0);
    }

    /** One catalog card: a single product, or all variants of one group. */
    static String item(Product p) {
        return p.getVariantGroup() == null ? "P" + p.getId() : "G" + p.getVariantGroup();
    }

    static Facets count(List<Product> products, ProductFilter f) {
        long total = items(products, f::matches);

        ProductFilter noCat = f.without("category");
        List<Option> categories = options(products, noCat, p -> p.getCategory() == null ? null : p.getCategory().trim().toLowerCase(java.util.Locale.ROOT),
                Comparator.naturalOrder());

        ProductFilter noPrice = f.without("price");
        List<PriceBand> prices = BANDS.stream().map(b -> new PriceBand(b.key(), b.min(), b.max(), items(products,
                p -> noPrice.matches(p) && p.getPrice().compareTo(b.min()) >= 0 && (b.max() == null || p.getPrice().compareTo(b.max()) <= 0))))
                .toList();

        ProductFilter noRating = f.without("rating");
        List<RatingOption> ratings = List.of(4, 3, 2, 1).stream().map(r -> new RatingOption(r, items(products,
                p -> noRating.matches(p) && p.getRatingAverage().compareTo(BigDecimal.valueOf(r)) >= 0))).toList();

        ProductFilter noStock = f.without("inStock");
        long inStock = items(products, p -> noStock.matches(p) && p.getStock() > 0);

        List<Option> colors = options(products, f.without("color"), Product::getColor, String.CASE_INSENSITIVE_ORDER);
        List<Option> sizes = options(products, f.without("size"), Product::getSize, VariantSummary.SIZES);
        return new Facets(total, categories, prices, ratings, inStock, colors, sizes);
    }

    /** Distinct items with at least one product passing the test. */
    static long items(List<Product> products, Predicate<Product> test) {
        return products.stream().filter(test).map(Facets::item).distinct().count();
    }

    /** value -> number of items having a product with that value that passes the filter; values without items are left out. */
    private static List<Option> options(List<Product> products, ProductFilter f, Function<Product, String> value, Comparator<String> order) {
        Map<String, Set<String>> byValue = new TreeMap<>(order);
        Map<String, String> shown = new LinkedHashMap<>();      // the first spelling seen, for display
        for (Product p : products) {
            String v = value.apply(p);
            if (v == null || v.isBlank() || !f.matches(p)) continue;
            String key = v.trim();
            shown.putIfAbsent(key.toLowerCase(java.util.Locale.ROOT), key);
            byValue.computeIfAbsent(shown.get(key.toLowerCase(java.util.Locale.ROOT)), k -> new java.util.HashSet<>()).add(item(p));
        }
        List<Option> out = new ArrayList<>();
        byValue.forEach((v, items) -> out.add(new Option(v, items.size())));
        return out;
    }
}
