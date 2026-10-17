package com.rbctcsworld.ecommerce.product;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET    /api/products?q=&page=0&size=20&sort=id|name|price_asc|price_desc
 *                           public           200 (JSON array = one page) | 400 bad page/size/sort
 *        headers: X-Total-Count, X-Total-Pages, X-Page, X-Page-Size, Link (rel="next"/"prev")
 *        The body stayed an array, so older clients keep working (backward-compatible fix of DEF-007).
 *        &grouped=true: one card per variant group (the storefront)
 * GET    /api/products/{id} public           200 | 404
 * GET    /api/products/{id}/variants   public  200 every size/colour of the item | 404
 * GET    /api/products/variant-groups?names=A,B  public  200 option summary per group | 400 more than 100
 * POST   /api/products      ADMIN            201 | 400 | 401 | 403 | 409
 * PUT    /api/products/{id} ADMIN            200 | 400 | 401 | 403 | 404 | 409
 * DELETE /api/products/{id} ADMIN (soft)     204 | 401 | 403 | 404
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;
    private final ProductImagePolicy images;

    public ProductController(ProductService service, ProductImagePolicy images) {
        this.service = service;
        this.images = images;
    }

    @GetMapping
    public ResponseEntity<List<Product>> all(@RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "" + ProductService.DEFAULT_PAGE_SIZE) int size,
                                             @RequestParam(defaultValue = "id") String sort,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) java.math.BigDecimal minPrice,
                                             @RequestParam(required = false) java.math.BigDecimal maxPrice,
                                             @RequestParam(required = false) Boolean inStock,
                                             @RequestParam(required = false) Boolean grouped) {
        Page<Product> result = service.page(q, page, size, sort, new ProductFilter(category, minPrice, maxPrice, inStock, grouped));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(result.getTotalPages()))
                .header("X-Page", String.valueOf(result.getNumber()))
                .header("X-Page-Size", String.valueOf(result.getSize()));
        String links = links(result);
        if (!links.isEmpty()) response.header("Link", links);
        return response.body(result.getContent());
    }

    /** RFC 8288 Link header, like the GitHub API: <...?page=1>; rel="next", <...?page=0>; rel="prev". */
    private static String links(Page<?> p) {
        StringBuilder sb = new StringBuilder();
        if (p.hasNext()) sb.append(link(p.getNumber() + 1, "next"));
        if (p.hasPrevious()) sb.append(sb.isEmpty() ? "" : ", ").append(link(p.getNumber() - 1, "prev"));
        return sb.toString();
    }

    private static String link(int page, String rel) {
        String url = ServletUriComponentsBuilder.fromCurrentRequest().replaceQueryParam("page", page).build().toUriString();
        return "<" + url + ">; rel=\"" + rel + "\"";
    }

    /** The categories of the products on sale (for the category filter). */
    @GetMapping("/categories")
    public List<String> categories() {
        return service.categories();
    }

    /** "Similar products" for the product page: same category, in stock first, closest price. */
    @GetMapping("/{id}/similar")
    public List<Product> similar(@PathVariable long id, @RequestParam(required = false) Integer limit) {
        return service.similar(id, limit);
    }

    /** Every size and colour of the item this product belongs to (itself included), for the product page. */
    @GetMapping("/{id}/variants")
    public List<Product> variants(@PathVariable long id) {
        return service.variants(id);
    }

    /** Option summaries for catalog cards, one request for a whole page of cards. */
    @GetMapping("/variant-groups")
    public List<VariantSummary> variantGroups(@RequestParam(required = false) List<String> names) {
        return service.variantSummaries(names);
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@Valid @RequestBody ProductRequest r) {
        images.check(r.imageUrl());
        return service.create(r);
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable long id, @Valid @RequestBody ProductRequest r) {
        images.check(r.imageUrl());
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
