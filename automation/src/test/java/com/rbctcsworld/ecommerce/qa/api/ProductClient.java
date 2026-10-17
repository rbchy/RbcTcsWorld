package com.rbctcsworld.ecommerce.qa.api;

import io.restassured.response.Response;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductClient extends ApiClient {

    public Response list() {
        return anonymous().get("/api/products");
    }

    public Response search(String q) {
        return anonymous().queryParam("q", q).get("/api/products");
    }

    /** One catalog page. Any argument may be null (= not sent), so defaults and bad values can be tested. */
    public Response page(String q, Object page, Object size, String sort) {
        var spec = anonymous();
        if (q != null) spec = spec.queryParam("q", q);
        if (page != null) spec = spec.queryParam("page", page);
        if (size != null) spec = spec.queryParam("size", size);
        if (sort != null) spec = spec.queryParam("sort", sort);
        return spec.get("/api/products");
    }

    /** Catalog search with any query parameters (advanced search: category, minPrice, maxPrice, inStock ...). */
    public Response search(Map<String, ?> query) {
        return anonymous().queryParams(query).get("/api/products");
    }

    public Response categories() {
        return anonymous().get("/api/products/categories");
    }

    /** Raw GET for negative tests with malformed paths. */
    public Response anonymousGet(String path) {
        return anonymous().get(path);
    }

    public Response get(long id) {
        return anonymous().get("/api/products/{id}", id);
    }

    public Response create(String token, Map<String, Object> body) {
        return as(token).body(body).post("/api/products");
    }

    public Response update(String token, long id, Map<String, Object> body) {
        return as(token).body(body).put("/api/products/{id}", id);
    }

    public Response delete(String token, long id) {
        return as(token).delete("/api/products/{id}", id);
    }

    /** Finds the id of an active product by SKU (seed data has fixed SKUs). */
    /** Walks the catalog page by page (the list is paginated since DEF-007). */
    public long idBySku(String sku) {
        for (int page = 0; page < 1000; page++) {
            List<Map<String, Object>> products = page(null, page, 100, null).then().statusCode(200)
                    .extract().jsonPath().getList("$");
            if (products.isEmpty()) break;
            for (Map<String, Object> p : products) {
                if (sku.equals(p.get("sku"))) return ((Number) p.get("id")).longValue();
            }
        }
        throw new IllegalStateException("No active product with SKU " + sku);
    }

    public static Map<String, Object> body(String name, String sku, String category, BigDecimal price, int stock) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("sku", sku);
        m.put("category", category);
        m.put("price", price);
        m.put("stock", stock);
        return m;
    }
}
