package com.rbctcsworld.ecommerce.cart;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.cart.CartDtos.CartLine;
import com.rbctcsworld.ecommerce.cart.CartDtos.CartResponse;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.product.Product;
import com.rbctcsworld.ecommerce.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business rules:
 *  1. Only active products can be added.
 *  2. Adding a product already in the cart increases its quantity (no duplicate lines).
 *  3. Quantity per line: 1..MAX_QTY_PER_ITEM.
 *  4. Quantity may not exceed current stock (409 Conflict).
 *  5. A customer can only see/change their own cart items (others' ids give 404).
 *  Prices are NOT stored in the cart; the current product price is always shown.
 */
@Service
@Transactional
public class CartService {

    public static final int MAX_QTY_PER_ITEM = 10;

    private final CartItemRepository items;
    private final ProductRepository products;
    private final UserRepository users;

    public CartService(CartItemRepository items, ProductRepository products, UserRepository users) {
        this.items = items;
        this.products = products;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(String email) {
        return buildCart(user(email).getId());
    }

    public CartResponse addItem(String email, Long productId, int quantity) {
        AppUser user = user(email);
        Product product = products.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));

        CartItem item = items.findByUserIdAndProductId(user.getId(), product.getId()).orElse(null);
        int newQuantity = (item == null ? 0 : item.getQuantity()) + quantity;
        validateQuantity(product, newQuantity);

        if (item == null) {
            item = new CartItem(user.getId(), product, newQuantity);
        } else {
            item.setQuantity(newQuantity);
        }
        items.save(item);
        return buildCart(user.getId());
    }

    public CartResponse updateItem(String email, Long itemId, int quantity) {
        AppUser user = user(email);
        CartItem item = ownedItem(itemId, user.getId());
        validateQuantity(item.getProduct(), quantity);
        item.setQuantity(quantity);
        items.save(item);
        return buildCart(user.getId());
    }

    public CartResponse removeItem(String email, Long itemId) {
        AppUser user = user(email);
        items.delete(ownedItem(itemId, user.getId()));
        return buildCart(user.getId());
    }

    public void clear(String email) {
        items.deleteByUserId(user(email).getId());
    }

    private void validateQuantity(Product product, int quantity) {
        if (quantity < 1 || quantity > MAX_QTY_PER_ITEM) {
            throw new BusinessRuleException("Quantity per item must be between 1 and " + MAX_QTY_PER_ITEM);
        }
        if (!product.isActive()) {
            throw new NotFoundException("Product not found: " + product.getId());
        }
        if (quantity > product.getStock()) {
            throw new ConflictException("Insufficient stock for " + product.getSku()
                    + ": requested " + quantity + ", available " + product.getStock());
        }
    }

    private CartItem ownedItem(Long itemId, Long userId) {
        return items.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new NotFoundException("Cart item not found: " + itemId));
    }

    private AppUser user(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private CartResponse buildCart(Long userId) {
        List<CartLine> lines = items.findByUserIdOrderByIdAsc(userId).stream().map(this::toLine).toList();
        int totalQuantity = lines.stream().mapToInt(CartLine::quantity).sum();
        BigDecimal subtotal = lines.stream().map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(lines, totalQuantity, subtotal);
    }

    private CartLine toLine(CartItem i) {
        Product p = i.getProduct();
        BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(i.getQuantity()));
        boolean available = p.isActive() && p.getStock() >= i.getQuantity();
        return new CartLine(i.getId(), p.getId(), p.getSku(), p.displayName(), p.getPrice(),
                i.getQuantity(), lineTotal, available);
    }
}
