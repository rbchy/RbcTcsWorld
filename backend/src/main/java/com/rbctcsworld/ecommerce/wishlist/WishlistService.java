package com.rbctcsworld.ecommerce.wishlist;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.cart.CartDtos.CartResponse;
import com.rbctcsworld.ecommerce.cart.CartService;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.product.Product;
import com.rbctcsworld.ecommerce.product.ProductRepository;
import com.rbctcsworld.ecommerce.wishlist.WishlistDtos.AddResult;
import com.rbctcsworld.ecommerce.wishlist.WishlistDtos.WishlistLine;
import com.rbctcsworld.ecommerce.wishlist.WishlistDtos.WishlistResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Business rules:
 *  1. Only active products can be added (404 otherwise).
 *  2. Adding the same product twice is idempotent: no duplicate, 200 instead of 201.
 *  3. At most MAX_ITEMS products per wishlist (400).
 *  4. The price at the moment of adding is kept, so the customer sees a price drop.
 *  5. Move to cart = add 1 to the cart (all cart rules apply, e.g. 409 out of stock) and remove from
 *     the wishlist - in one transaction: if the cart refuses, the product STAYS on the wishlist.
 *  6. Removing a product that is not on the wishlist gives 404.
 */
@Service
@Transactional
public class WishlistService {

    public static final int MAX_ITEMS = 50;

    private final WishlistItemRepository items;
    private final ProductRepository products;
    private final UserRepository users;
    private final CartService cart;
    private final Clock clock;

    public WishlistService(WishlistItemRepository items, ProductRepository products, UserRepository users,
                           CartService cart, Clock clock) {
        this.items = items;
        this.products = products;
        this.users = users;
        this.cart = cart;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WishlistResponse get(String email) {
        return build(user(email).getId());
    }

    public AddResult add(String email, Long productId) {
        AppUser user = user(email);
        Product product = products.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
        if (items.findByUserIdAndProductId(user.getId(), productId).isPresent()) {
            return new AddResult(false, build(user.getId()));
        }
        if (items.countByUserId(user.getId()) >= MAX_ITEMS) {
            throw new BusinessRuleException("A wishlist can hold at most " + MAX_ITEMS + " products");
        }
        items.save(new WishlistItem(user.getId(), product, LocalDateTime.now(clock)));
        return new AddResult(true, build(user.getId()));
    }

    public WishlistResponse remove(String email, Long productId) {
        AppUser user = user(email);
        items.delete(owned(user.getId(), productId));
        return build(user.getId());
    }

    public CartResponse moveToCart(String email, Long productId) {
        AppUser user = user(email);
        WishlistItem item = owned(user.getId(), productId);
        CartResponse result = cart.addItem(email, productId, 1);   // throws -> rollback, item stays
        items.delete(item);
        return result;
    }

    private WishlistItem owned(Long userId, Long productId) {
        return items.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " is not on your wishlist"));
    }

    private WishlistResponse build(Long userId) {
        List<WishlistLine> lines = items.findByUserIdOrderByIdDesc(userId).stream().map(WishlistService::toLine).toList();
        return new WishlistResponse(lines, lines.size());
    }

    static WishlistLine toLine(WishlistItem i) {
        Product p = i.getProduct();
        BigDecimal drop = i.getPriceWhenAdded().subtract(p.getPrice());
        if (drop.signum() < 0) drop = BigDecimal.ZERO;
        return new WishlistLine(p.getId(), p.getSku(), p.displayName(), i.getPriceWhenAdded(), p.getPrice(),
                drop.setScale(2), p.isActive() && p.getStock() > 0, p.getRatingAverage(), p.getRatingCount(),
                i.getCreatedAt());
    }

    private AppUser user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
