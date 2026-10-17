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

    public ProductService(ProductRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    /** The product fields the audit trail follows (ratings change with every review and are not audited). */
    static Map<String, Object> audited(Product p) {
        return AuditChange.fields("name", p.getName(), "sku", p.getSku(), "category", p.getCategory(),
                "price", p.getPrice(), "stock", p.getStock(), "imageUrl", p.getImageUrl(), "active", p.isActive());
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
        PageRequest first = PageRequest.of(0, n);
        return p.getCategory() == null || p.getCategory().isBlank()
                ? repo.similarByPrice(p.getId(), p.getPrice(), first)
                : repo.similarInCategory(p.getId(), p.getCategory(), p.getPrice(), first);
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
        Product p = new Product(r.name().trim(), sku, r.category(), r.price(), r.stock());
        p.changeImage(r.imageUrl());
        Product saved = repo.save(p);
        audit(saved, "PRODUCT_CREATED", null);
        return saved;
    }

    public Product update(long id, ProductRequest r) {
        Product p = get(id);
        String sku = r.sku().trim();
        repo.findBySku(sku)
                .filter(other -> !other.getId().equals(p.getId()))
                .ifPresent(other -> { throw new ConflictException("SKU already exists: " + sku); });
        Map<String, Object> before = audited(p);
        p.update(r.name().trim(), sku, r.category(), r.price(), r.stock());
        p.changeImage(r.imageUrl());                                  // PUT replaces: no imageUrl = no picture
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
