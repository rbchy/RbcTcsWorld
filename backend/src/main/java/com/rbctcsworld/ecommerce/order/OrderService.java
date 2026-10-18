package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.audit.AuditEvent;
import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.cart.CartItem;
import com.rbctcsworld.ecommerce.cart.CartItemRepository;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.common.exception.PaymentDeclinedException;
import com.rbctcsworld.ecommerce.coupon.Coupon;
import com.rbctcsworld.ecommerce.coupon.CouponService;
import com.rbctcsworld.ecommerce.inventory.InventoryService;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderLine;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderResponse;
import com.rbctcsworld.ecommerce.order.OrderDtos.QuoteResponse;
import com.rbctcsworld.ecommerce.order.OrderDtos.TrackingResponse;
import com.rbctcsworld.ecommerce.payment.PaymentDtos.PayRequest;
import com.rbctcsworld.ecommerce.payment.PaymentService;
import com.rbctcsworld.ecommerce.payment.PaymentTransaction;
import com.rbctcsworld.ecommerce.pricing.PricingService;
import com.rbctcsworld.ecommerce.pricing.PricingService.PriceBreakdown;
import com.rbctcsworld.ecommerce.product.Product;
import com.rbctcsworld.ecommerce.notification.OrderNotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Place order = ONE database transaction:
 *   1. read the customer's cart (400 if empty) and check that there is a delivery address (400 if not)
 *   2. validate the optional coupon and calculate discount, shipping, tax, total
 *   3. for every line (sorted by product id to avoid deadlocks) reserve stock atomically (409 if not possible)
 *   4. save order + items + stock movements + coupon redemption
 *   5. empty the cart
 * If any step fails, everything is rolled back: no stock changes, no order, no coupon use, cart untouched.
 *
 * Pay  = PLACED -> PAID with a successful charge (a declined card is recorded but the order stays PLACED).
 * Cancel = PLACED/PAID -> CANCELLED, stock released, and a refund if it was PAID.
 */
@Service
@Transactional
public class OrderService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I confusion
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orders;
    private final CartItemRepository cartItems;
    private final UserRepository users;
    private final InventoryService inventory;
    private final PricingService pricing;
    private final CouponService coupons;
    private final PaymentService payments;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orders, CartItemRepository cartItems, UserRepository users,
                        InventoryService inventory, PricingService pricing, CouponService coupons,
                        PaymentService payments, ApplicationEventPublisher events) {
        this.orders = orders;
        this.cartItems = cartItems;
        this.users = users;
        this.inventory = inventory;
        this.pricing = pricing;
        this.coupons = coupons;
        this.payments = payments;
        this.events = events;
    }

    // ---------------------------------------------------------------- checkout

    /** Price preview for the current cart (+ optional coupon). Read-only: nothing is reserved or redeemed. */
    @Transactional(readOnly = true)
    public QuoteResponse quote(String email, String couponCode) {
        AppUser user = user(email);
        List<CartItem> lines = cartLines(user);
        List<OrderLine> orderLines = lines.stream().map(OrderService::toLine).toList();
        BigDecimal subtotal = orderLines.stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Coupon coupon = hasText(couponCode) ? coupons.validate(couponCode, user.getId(), subtotal) : null;
        PriceBreakdown p = pricing.price(subtotal, coupon == null ? BigDecimal.ZERO : coupon.discountFor(subtotal));
        int qty = orderLines.stream().mapToInt(OrderLine::quantity).sum();
        return new QuoteResponse(orderLines, qty, p.subtotal(), coupon == null ? null : coupon.getCode(),
                p.discount(), p.shippingFee(), p.tax(), p.total());
    }

    public OrderResponse placeOrder(String email, String couponCode, ShippingAddress address) {
        AppUser user = user(email);
        List<CartItem> lines = cartLines(user);
        if (address == null) {
            throw new BusinessRuleException("Shipping address is required");
        }

        CustomerOrder order = new CustomerOrder(newOrderNumber(), user.getId());
        order.shipTo(address.normalized());
        for (CartItem line : lines) {
            Product p = line.getProduct();
            order.addItem(new OrderItem(p.getId(), p.getSku(), p.displayName(), p.getPrice(), line.getQuantity()));
        }

        Coupon coupon = hasText(couponCode) ? coupons.validate(couponCode, user.getId(), order.getSubtotal()) : null;
        PriceBreakdown price = pricing.price(order.getSubtotal(),
                coupon == null ? BigDecimal.ZERO : coupon.discountFor(order.getSubtotal()));
        order.applyPricing(price.discount(), price.shippingFee(), price.tax(), price.total(),
                coupon == null ? null : coupon.getCode());
        orders.save(order); // gets the id used by stock movements / redemption

        for (CartItem line : lines) {
            inventory.reserve(line.getProduct(), line.getQuantity(), order.getId(), address.state());
        }
        if (coupon != null) {
            coupons.redeem(coupon, user.getId(), order.getId());
        }

        cartItems.deleteByUserId(user.getId());
        events.publishEvent(OrderNotificationEvent.of(order.getId(), OrderNotificationEvent.Type.ORDER_PLACED));
        return OrderResponse.from(order);
    }

    /**
     * noRollbackFor: a declined card must still leave its FAILED payment_transactions row in the database.
     */
    @Transactional(noRollbackFor = PaymentDeclinedException.class)
    public OrderResponse pay(String email, Long orderId, PayRequest card) {
        CustomerOrder order = owned(email, orderId);
        order.assertCanMoveTo(OrderStatus.PAID);                      // 409 before any money moves
        payments.charge(order.getId(), order.getTotal(), card);   // throws 400 / 402 on problems
        String from = order.getStatus();
        order.markPaid();
        events.publishEvent(AuditEvent.orderStatus(order.getId(), from, order.getStatus()));
        events.publishEvent(OrderNotificationEvent.of(order.getId(), OrderNotificationEvent.Type.PAYMENT_RECEIVED));
        return OrderResponse.from(order);
    }

    // ---------------------------------------------------------------- after checkout

    @Transactional(readOnly = true)
    public List<OrderResponse> myOrders(String email) {
        return orders.findByUserIdOrderByIdDesc(user(email).getId()).stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse myOrder(String email, Long orderId) {
        return OrderResponse.from(owned(email, orderId));
    }

    @Transactional(readOnly = true)
    public TrackingResponse myTracking(String email, Long orderId) {
        return TrackingResponse.from(owned(email, orderId));
    }

    @Transactional(readOnly = true)
    public List<PaymentTransaction> myPayments(String email, Long orderId) {
        return payments.history(owned(email, orderId).getId());
    }

    public OrderResponse cancel(String email, Long orderId) {
        CustomerOrder order = owned(email, orderId);
        order.assertCanMoveTo(OrderStatus.CANCELLED);                 // SHIPPED and later: 409
        order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .forEach(i -> inventory.release(i.getProductId(), i.getQuantity(), order.getId()));
        BigDecimal refunded = null;
        if (order.isPaid()) {
            payments.refund(order.getId(), order.getTotal());
            refunded = order.getTotal();
        }
        String from = order.getStatus();
        order.cancel();
        events.publishEvent(AuditEvent.orderStatus(order.getId(), from, order.getStatus()));
        events.publishEvent(new OrderNotificationEvent(order.getId(), OrderNotificationEvent.Type.ORDER_CANCELLED, refunded));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> allOrders() {
        return orders.findAllByOrderByIdDesc().stream().map(OrderResponse::from).toList();
    }

    // ---------------------------------------------------------------- helpers

    private List<CartItem> cartLines(AppUser user) {
        List<CartItem> lines = cartItems.findByUserIdOrderByIdAsc(user.getId()).stream()
                .sorted(Comparator.comparing(ci -> ci.getProduct().getId()))
                .toList();
        if (lines.isEmpty()) {
            throw new BusinessRuleException("Cart is empty");
        }
        return lines;
    }

    private static OrderLine toLine(CartItem ci) {
        Product p = ci.getProduct();
        return new OrderLine(p.getId(), p.getSku(), p.displayName(), p.getPrice(), ci.getQuantity(),
                p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
    }

    /** Also used by ReturnService: the order if it belongs to this customer, otherwise 404 (IDOR). */
    public CustomerOrder owned(String email, Long orderId) {
        return orders.findByIdAndUserId(orderId, user(email).getId())
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
    }

    private AppUser user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** e.g. ORD-20261002-7KQ2MZ */
    static String newOrderNumber() {
        StringBuilder sb = new StringBuilder("ORD-")
                .append(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)).append('-');
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
