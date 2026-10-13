package com.rbctcsworld.ecommerce.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(length = 100)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private boolean active = true;

    /** Average of PUBLISHED reviews, one decimal (0.0 when none). Maintained by ReviewService. */
    @Column(name = "rating_average", nullable = false, precision = 2, scale = 1)
    private BigDecimal ratingAverage = BigDecimal.ZERO.setScale(1);

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    protected Product() {
    }

    public Product(String name, String sku, String category, BigDecimal price, int stock) {
        this.name = name;
        this.sku = sku;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    /** Applies every editable field (the old version only changed stock - a real defect). */
    public void update(String name, String sku, String category, BigDecimal price, int stock) {
        this.name = name;
        this.sku = sku;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public int getStock() { return stock; }
    public boolean isActive() { return active; }
    public BigDecimal getRatingAverage() { return ratingAverage; }
    public int getRatingCount() { return ratingCount; }
    public String getImageUrl() { return imageUrl; }

    /** null or blank = no own picture (the storefront shows the category picture). */
    public void changeImage(String imageUrl) {
        this.imageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim();
    }

    public void setActive(boolean active) { this.active = active; }
    public void setStock(int stock) { this.stock = stock; }

    public void applyRating(BigDecimal average, int count) {
        this.ratingAverage = average;
        this.ratingCount = count;
    }
}
