package com.rbctcsworld.ecommerce.store;

import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * STORE_SALES=off (ADR-0026): every way to buy from the shop itself answers 409 with one plain sentence. The
 * security rules run first, so a request without a login is still 401. Admin pages and partner products are
 * untouched.
 */
@Configuration
public class SalesClosedInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    public static final String CLOSED = "This site does not sell its own products yet. Partner products are bought "
            + "at the partner's shop.";

    /** Cart, checkout, orders (with returns and invoices), payments and saved cards, guest checkout. */
    static final List<String> CLOSED_PATHS = List.of("/api/cart", "/api/cart/**", "/api/checkout", "/api/checkout/**", "/api/orders",
            "/api/orders/**", "/api/payments/**", "/api/payment-methods", "/api/payment-methods/**", "/api/auth/guest",
            "/api/wishlist/*/move-to-cart");

    private final StoreSales sales;

    public SalesClosedInterceptor(StoreSales sales) {
        this.sales = sales;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!sales.on()) throw new ConflictException(CLOSED);
        return true;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(CLOSED_PATHS);
    }
}
