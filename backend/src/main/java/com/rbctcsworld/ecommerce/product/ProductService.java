package com.rbctcsworld.ecommerce.product;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Locale;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.audit.AuditChange;
import com.rbctcsworld.ecommerce.audit.AuditEvent;
import com.rbctcsworld.ecommerce.inventory.InventoryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ProductService {

    private final ProductRepository repo;
    private final ApplicationEventPublisher events;
    private final InventoryService inventory;

    public ProductService(ProductRepository repo, ApplicationEventPublisher events, InventoryService inventory) {
        this.repo = repo;
        this.events = events;
        this.inventory = inventory;
    }

    /** The product fields the audit trail follows (ratings change with every review and are not audited). */
    static Map<String, Object> audited(Product p) {
        return AuditChange.fields("name", p.getName(), "sku", p.getSku(), "category", p.getCategory(),
                "price", p.getPrice(), "stock", p.getStock(), "imageUrl", p.getImageUrl(), "active", p.isActive(),
                "variantGroup", p.getVariantGroup(), "size", p.getSize(), "color", p.getColor());
    }

    private void audit(Product p, String action, Map<String, Object> before) {
        events.publishEvent(AuditEvent.of(AuditChange.diff(AuditChange.PRODUCT, p.getId(), action, before, audited(p))));
    }

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final List<String> SORTS = List.of("id", "name", "price_asc", "price_desc");

    /**
     * DEF-007: one page of the active catalog instead of every product. Page 0-based, size 1..100.
     * Every sort ends with the id, so pages are stable (no product appears twice or goes missing).
     */
    @Transactional(readOnly = true)
    public Page<Product> page(String q, int page, int size, String sort) {
        return page(q, page, size, sort, ProductFilter.NONE);
    }

    /**
     * Like {@link #page(String, int, int, String)} with the extra filters of {@link ProductFilter}.
     * minPrice/maxPrice may not be negative and maxPrice may not be below minPrice (400).
     */
    @Transactional(readOnly = true)
    public Page<Product> page(String q, int page, int size, String sort, ProductFilter filter) {
        if (page < 0) throw new BusinessRuleException("page must be 0 or greater");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new BusinessRuleException("size must be between 1 and " + MAX_PAGE_SIZE);
        String s = sort == null || sort.isBlank() ? "id" : sort.trim().toLowerCase();
        if (!SORTS.contains(s)) throw new BusinessRuleException("sort must be one of " + SORTS);
        PageRequest request = PageRequest.of(page, size, sortOf(s));
        ProductFilter f = filter == null ? ProductFilter.NONE : filter;
        if (f.minPrice() != null && f.minPrice().signum() < 0) throw new BusinessRuleException("minPrice cannot be negative");
        if (f.maxPrice() != null && f.maxPrice().signum() < 0) throw new BusinessRuleException("maxPrice cannot be negative");
        if (f.minPrice() != null && f.maxPrice() != null && f.maxPrice().compareTo(f.minPrice()) < 0) {
            throw new BusinessRuleException("maxPrice must not be below minPrice");
        }
        if (!f.isEmpty()) return repo.findAll(spec(q, f), request);
        return (q == null || q.isBlank())
                ? repo.findByActiveTrue(request)
                : repo.findByNameContainingIgnoreCaseAndActiveTrue(q.trim(), request);
    }

    /** active AND name contains q AND category = ... AND minPrice <= price <= maxPrice AND stock > 0 (each when given). */
    static Specification<Product> spec(String q, ProductFilter f) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.<Boolean>get("active")));
            if (q != null && !q.isBlank()) {
                where.add(cb.like(cb.lower(root.<String>get("name")), "%" + q.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (f.category() != null && !f.category().isBlank()) {
                where.add(cb.equal(cb.lower(root.<String>get("category")), f.category().trim().toLowerCase(Locale.ROOT)));
            }
            if (f.minPrice() != null) where.add(cb.greaterThanOrEqualTo(root.<java.math.BigDecimal>get("price"), f.minPrice()));
            if (f.maxPrice() != null) where.add(cb.lessThanOrEqualTo(root.<java.math.BigDecimal>get("price"), f.maxPrice()));
            if (Boolean.TRUE.equals(f.inStock())) where.add(cb.greaterThan(root.<Integer>get("stock"), 0));
            if (Boolean.TRUE.equals(f.grouped())) {
                // one card per item: a single product, or the first variant (lowest id) on sale of its group
                var first = query.subquery(Long.class);
                var v = first.from(Product.class);
                first.select(cb.min(v.<Long>get("id")))
                        .where(cb.equal(v.get("variantGroup"), root.get("variantGroup")), cb.isTrue(v.<Boolean>get("active")));
                where.add(cb.or(cb.isNull(root.get("variantGroup")), cb.equal(root.get("id"), first)));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    public static final int MAX_SIMILAR = 12;

    /**
     * Up to {@code limit} (1..12, default 4) products like this one: same category, in stock first,
     * closest price first. 404 when the product itself is not on sale.
     */
    @Transactional(readOnly = true)
    public List<Product> similar(long id, Integer limit) {
        int n = limit == null ? 4 : limit;
        if (n < 1 || n > MAX_SIMILAR) throw new BusinessRuleException("limit must be between 1 and " + MAX_SIMILAR);
        Product p = get(id);
        // other sizes and colours of the same item are not "similar products" (the product page offers them)
        int siblings = p.getVariantGroup() == null ? 0 : repo.variantsOf(p.getVariantGroup()).size();
        PageRequest first = PageRequest.of(0, n + siblings);
        List<Product> found = p.getCategory() == null || p.getCategory().isBlank()
                ? repo.similarByPrice(p.getId(), p.getPrice(), first)
                : repo.similarInCategory(p.getId(), p.getCategory(), p.getPrice(), first);
        return found.stream()
                .filter(o -> p.getVariantGroup() == null || !p.getVariantGroup().equals(o.getVariantGroup()))
                .limit(n).toList();
    }

    /** Every size and colour of this product's item (itself included); a product without variants: just itself. */
    @Transactional(readOnly = true)
    public List<Product> variants(long id) {
        Product p = get(id);
        return p.getVariantGroup() == null ? List.of(p) : repo.variantsOf(p.getVariantGroup());
    }

    public static final int MAX_GROUPS = 100;

    /** For catalog cards: per variant group, how many options, which colours and sizes, price range, units. */
    @Transactional(readOnly = true)
    public List<VariantSummary> variantSummaries(List<String> groups) {
        List<String> wanted = groups == null ? List.of() : groups.stream()
                .filter(g -> g != null && !g.isBlank()).map(g -> g.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        if (wanted.size() > MAX_GROUPS) throw new BusinessRuleException("at most " + MAX_GROUPS + " groups at a time");
        if (wanted.isEmpty()) return List.of();
        Map<String, List<Product>> byGroup = new java.util.LinkedHashMap<>();
        wanted.forEach(g -> byGroup.put(g, new ArrayList<>()));
        repo.variantsOfAll(wanted).forEach(p -> byGroup.get(p.getVariantGroup()).add(p));
        return byGroup.entrySet().stream().filter(e -> !e.getValue().isEmpty())
                .map(e -> VariantSummary.of(e.getKey(), e.getValue())).toList();
    }

    /**
     * Normalises and checks the variant fields: group in upper case, blanks are null; size or colour need a group,
     * a group needs a size or a colour (400); no two products on sale of one group with the same size and colour
     * (409, ignoring case).
     */
    VariantKey variantOf(ProductRequest r, Long selfId) {
        String group = blankToNull(r.variantGroup());
        String size = blankToNull(r.size());
        String color = blankToNull(r.color());
        if (group == null) {
            if (size != null || color != null) throw new BusinessRuleException("A size or colour needs a variant group");
            return new VariantKey(null, null, null);
        }
        group = group.toUpperCase(Locale.ROOT);
        if (size == null && color == null) throw new BusinessRuleException("A variant needs a size, a colour or both");
        for (Product other : repo.variantsOf(group)) {
            if (!other.getId().equals(selfId) && equalsIgnoreCase(other.getSize(), size) && equalsIgnoreCase(other.getColor(), color)) {
                throw new ConflictException("Variant group " + group + " already has " + label(color, size) + " (SKU " + other.getSku() + ")");
            }
        }
        return new VariantKey(group, size, color);
    }

    record VariantKey(String group, String size, String color) {}

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim().replaceAll("\\s+", " ");
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    private static String label(String color, String size) {
        return java.util.stream.Stream.of(color, size).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining(" / "));
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return repo.activeCategories();
    }

    static Sort sortOf(String sort) {
        Sort byId = Sort.by(Sort.Order.asc("id"));
        return switch (sort) {
            case "name" -> Sort.by(Sort.Order.asc("name").ignoreCase()).and(byId);
            case "price_asc" -> Sort.by(Sort.Order.asc("price")).and(byId);
            case "price_desc" -> Sort.by(Sort.Order.desc("price")).and(byId);
            default -> byId;
        };
    }

    /** Deleted (inactive) products are invisible: 404, same as never existed. */
    @Transactional(readOnly = true)
    public Product get(long id) {
        return repo.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }

    public Product create(ProductRequest r) {
        String sku = r.sku().trim();
        if (repo.existsBySku(sku)) {
            throw new ConflictException("SKU already exists: " + sku);
        }
        VariantKey variant = variantOf(r, null);
        Product p = new Product(r.name().trim(), sku, r.category(), r.price(), r.stock());
        p.changeImage(r.imageUrl());
        p.changeVariant(variant.group(), variant.size(), variant.color());
        Product saved = repo.save(p);
        inventory.stockNewProduct(saved.getId(), saved.getStock());       // the first units go to the default warehouse
        audit(saved, "PRODUCT_CREATED", null);
        return saved;
    }

    public Product update(long id, ProductRequest r) {
        Product p = get(id);
        String sku = r.sku().trim();
        repo.findBySku(sku)
                .filter(other -> !other.getId().equals(p.getId()))
                .ifPresent(other -> { throw new ConflictException("SKU already exists: " + sku); });
        VariantKey variant = variantOf(r, p.getId());
        Map<String, Object> before = audited(p);
        inventory.changeTotalFromProductForm(p.getId(), r.stock());        // atomic; the entity never writes stock
        p.update(r.name().trim(), sku, r.category(), r.price(), r.stock());
        p.changeImage(r.imageUrl());                                  // PUT replaces: no imageUrl = no picture
        p.changeVariant(variant.group(), variant.size(), variant.color());  // ... and no variant fields = no variant
        Product saved = repo.save(p);
        audit(saved, "PRODUCT_UPDATED", before);
        return saved;
    }

    /** Soft delete: keeps history for orders/audit, hides the product from customers. */
    public void delete(long id) {
        Product p = get(id);
        Map<String, Object> before = audited(p);
        p.setActive(false);
        repo.save(p);
        audit(p, "PRODUCT_DELETED", before);
    }
}
