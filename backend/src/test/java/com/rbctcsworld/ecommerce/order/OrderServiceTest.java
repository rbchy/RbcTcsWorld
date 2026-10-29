package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.cart.CartItem;
import com.rbctcsworld.ecommerce.cart.CartItemRepository;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.coupon.CouponService;
import com.rbctcsworld.ecommerce.inventory.InventoryService;
import com.rbctcsworld.ecommerce.payment.PaymentService;
import com.rbctcsworld.ecommerce.pricing.PricingService;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderResponse;
import com.rbctcsworld.ecommerce.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.rbctcsworld.ecommerce.coupon.Coupon;
import com.rbctcsworld.ecommerce.payment.PaymentDtos.PayRequest;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String EMAIL = "buyer@test.com";

    @Mock OrderRepository orders;
    @Mock CartItemRepository cartItems;
    @Mock UserRepository users;
    @Mock InventoryService inventory;
    @Mock CouponService coupons;
    @Mock PaymentService payments;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @Spy PricingService pricing = new PricingService(new BigDecimal("0.06"), new BigDecimal("50.00"), new BigDecimal("5.99"));
    @Spy com.rbctcsworld.ecommerce.tax.TaxCalculator taxes = new com.rbctcsworld.ecommerce.tax.FlatRateTaxCalculator(new BigDecimal("0.06"));
    @InjectMocks OrderService service;

    private Product mouse;
    private Product keyboard;

    private static Product product(long id, String sku, String price) {
        Product p = new Product(sku + " name", sku, "electronics", new BigDecimal(price), 10);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser(EMAIL, "hash");
        ReflectionTestUtils.setField(user, "id", 7L);
        lenient().when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user)); // not used by the order-number test
        mouse = product(1L, "SKU-M", "20.00");
        keyboard = product(2L, "SKU-K", "50.00");
    }

    @Test
    void emptyCartCannotBeOrdered() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.placeOrder(EMAIL, null, com.rbctcsworld.ecommerce.TestAddresses.ADDRESS))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Cart is empty");
        verify(orders, never()).save(any());
    }

    /** REQ-AFF-02: a product that became an affiliate product while in the cart cannot be ordered here (ADR-0020). */
    @Test
    void aPartnerProductInTheCartStopsTheOrder() {
        mouse.changeAffiliateUrl("https://www.amazon.com/dp/B000TEST?tag=shop-20");
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 1)));

        assertThatThrownBy(() -> service.placeOrder(EMAIL, null, com.rbctcsworld.ecommerce.TestAddresses.ADDRESS))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sold by our partner shop").hasMessageEndingWith("remove it from the cart");
        verify(orders, never()).save(any());
    }

    @Test
    void placeOrderSnapshotsPricesReservesStockAndClearsCart() {
        // cart order is keyboard then mouse; reservations must happen in product-id order (deadlock safety)
        when(cartItems.findByUserIdOrderByIdAsc(7L))
                .thenReturn(List.of(new CartItem(7L, keyboard, 1), new CartItem(7L, mouse, 2)));

        OrderResponse r = service.placeOrder(EMAIL, null, com.rbctcsworld.ecommerce.TestAddresses.ADDRESS);

        assertThat(r.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(r.orderNumber()).matches("ORD-\\d{8}-[A-HJ-NP-Z2-9]{6}");
        assertThat(r.totalQuantity()).isEqualTo(3);
        assertThat(r.subtotal()).isEqualByComparingTo("90.00");
        assertThat(r.shippingFee()).isEqualByComparingTo("0.00");   // >= 50 -> free shipping
        assertThat(r.tax()).isEqualByComparingTo("5.40");           // 6% of 90
        assertThat(r.total()).isEqualByComparingTo("95.40");
        assertThat(r.items()).extracting(OrderDtos.OrderLine::sku).containsExactly("SKU-M", "SKU-K");

        InOrder seq = inOrder(orders, inventory, cartItems);
        seq.verify(orders).save(any(CustomerOrder.class));
        seq.verify(inventory).reserve(mouse, 2, null, "PA");          // the delivery state picks the warehouse
        seq.verify(inventory).reserve(keyboard, 1, null, "PA");
        seq.verify(cartItems).deleteByUserId(7L);
        verify(events).publishEvent(com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.of(null, com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type.ORDER_PLACED));
    }

    @Test
    void anOrderWithoutAnAddressIsRejectedBeforeAnythingIsSaved() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 1)));

        assertThatThrownBy(() -> service.placeOrder(EMAIL, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Shipping address is required");
        verify(orders, never()).save(any());
        verifyNoInteractions(inventory, coupons);
        verify(cartItems, never()).deleteByUserId(any());
    }

    @Test
    void theAddressIsStoredTrimmedWithTheStateInCapitals() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 1)));
        ShippingAddress typed = new ShippingAddress("  Ada Lovelace ", " 12 Main St ", "   ", " Pittsburgh ", " pa ",
                " 15213-0001 ", "  ");

        OrderResponse r = service.placeOrder(EMAIL, null, typed);

        assertThat(r.shippingAddress()).isEqualTo(
                new ShippingAddress("Ada Lovelace", "12 Main St", null, "Pittsburgh", "PA", "15213-0001", null));
    }

    @Test
    void stockFailureLeavesCartUntouched() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 2)));
        doThrow(new ConflictException("Insufficient stock")).when(inventory).reserve(eq(mouse), anyInt(), any(), any());

        assertThatThrownBy(() -> service.placeOrder(EMAIL, null, com.rbctcsworld.ecommerce.TestAddresses.ADDRESS)).isInstanceOf(ConflictException.class);
        verify(cartItems, never()).deleteByUserId(any());
    }

    @Test
    void cancelReleasesEveryLineAndSetsStatus() {
        CustomerOrder order = new CustomerOrder("ORD-1", 7L);
        ReflectionTestUtils.setField(order, "id", 55L);
        order.addItem(new OrderItem(2L, "SKU-K", "K", new BigDecimal("50.00"), 1));
        order.addItem(new OrderItem(1L, "SKU-M", "M", new BigDecimal("20.00"), 2));
        when(orders.findByIdAndUserId(55L, 7L)).thenReturn(Optional.of(order));

        OrderResponse r = service.cancel(EMAIL, 55L);

        assertThat(r.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(r.cancelledAt()).isNotNull();
        InOrder seq = inOrder(inventory);
        seq.verify(inventory).release(1L, 2, 55L);
        seq.verify(inventory).release(2L, 1, 55L);
    }

    @Test
    void cancellingTwiceIsConflict() {
        CustomerOrder order = new CustomerOrder("ORD-1", 7L);
        order.addItem(new OrderItem(1L, "SKU-M", "M", new BigDecimal("20.00"), 1));
        order.cancel();
        when(orders.findByIdAndUserId(55L, 7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(EMAIL, 55L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CANCELLED");
        verify(inventory, never()).release(any(), anyInt(), any());
    }

    @Test
    void cancellingAPaidOrderRefundsTheTotal() {
        CustomerOrder order = new CustomerOrder("ORD-1", 7L);
        ReflectionTestUtils.setField(order, "id", 55L);
        order.addItem(new OrderItem(1L, "SKU-M", "M", new BigDecimal("20.00"), 1));
        order.applyPricing(BigDecimal.ZERO, new BigDecimal("5.99"), new BigDecimal("1.20"), new BigDecimal("27.19"), null);
        order.markPaid();
        when(orders.findByIdAndUserId(55L, 7L)).thenReturn(Optional.of(order));

        service.cancel(EMAIL, 55L);

        verify(payments).refund(55L, new BigDecimal("27.19"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(events).publishEvent(new com.rbctcsworld.ecommerce.notification.OrderNotificationEvent(55L, com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type.ORDER_CANCELLED, new BigDecimal("27.19")));
        verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.orderStatus(55L, "PAID", "CANCELLED"));
    }

    @Test
    void cancellingAnUnpaidOrderDoesNotRefund() {
        CustomerOrder order = new CustomerOrder("ORD-1", 7L);
        ReflectionTestUtils.setField(order, "id", 55L);
        order.addItem(new OrderItem(1L, "SKU-M", "M", new BigDecimal("20.00"), 1));
        when(orders.findByIdAndUserId(55L, 7L)).thenReturn(Optional.of(order));

        service.cancel(EMAIL, 55L);

        verify(payments, never()).refund(any(), any());
        verify(events).publishEvent(new com.rbctcsworld.ecommerce.notification.OrderNotificationEvent(55L, com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type.ORDER_CANCELLED, null));   // no refund line in the e-mail
        verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.orderStatus(55L, "PLACED", "CANCELLED"));
    }

    @Test
    void paidOrderCannotBePaidAgain() {
        CustomerOrder order = new CustomerOrder("ORD-1", 7L);
        order.addItem(new OrderItem(1L, "SKU-M", "M", new BigDecimal("20.00"), 1));
        order.markPaid();
        when(orders.findByIdAndUserId(55L, 7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.pay(EMAIL, 55L, null))
                .isInstanceOf(ConflictException.class).hasMessageContaining("PAID");
        // Found by mutation testing (PIT): this line used anyLong(), which does NOT match null (the order has no
        // id here), so it passed even when the card WAS charged. any() matches null too.
        verify(payments, never()).charge(any(), any(), any());
    }

    @Test
    void anotherCustomersOrderIsNotFound() {
        when(orders.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myOrder(EMAIL, 99L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.cancel(EMAIL, 99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void orderNumbersAreUniqueAndReadable() {
        assertThat(OrderService.newOrderNumber()).matches("ORD-\\d{8}-[A-HJ-NP-Z2-9]{6}").hasSize(19);
        assertThat(OrderService.newOrderNumber()).isNotEqualTo(OrderService.newOrderNumber());
    }

    // ---- added after mutation testing (PIT): the coupon path of placeOrder, a successful payment and the
    // ---- order -> item link were never checked by a unit test.

    @Test
    void blankCouponCodeMeansNoCoupon() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 1)));

        OrderResponse r = service.placeOrder(EMAIL, "   ", com.rbctcsworld.ecommerce.TestAddresses.ADDRESS);

        assertThat(r.couponCode()).isNull();
        assertThat(r.discount()).isEqualByComparingTo("0.00");
        assertThat(r.customerId()).isEqualTo(7L);
        verifyNoInteractions(coupons);
    }

    @Test
    void couponIsValidatedAppliedBeforeShippingAndRedeemed() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, keyboard, 1)));
        Coupon save10 = new Coupon("SAVE10", Coupon.PERCENT, new BigDecimal("10"), null, null, null, null);
        when(coupons.validate(eq("save10"), eq(7L), any())).thenReturn(save10);

        OrderResponse r = service.placeOrder(EMAIL, "save10", com.rbctcsworld.ecommerce.TestAddresses.ADDRESS);

        assertThat(r.couponCode()).isEqualTo("SAVE10");
        assertThat(r.discount()).isEqualByComparingTo("5.00");
        // 50.00 - 5.00 = 45.00 is below the 50.00 free-shipping threshold: 45.00 + 5.99 + 6% tax 2.70
        assertThat(r.shippingFee()).isEqualByComparingTo("5.99");
        assertThat(r.total()).isEqualByComparingTo("53.69");
        verify(coupons).redeem(eq(save10), eq(7L), any());
    }

    @Test
    void everyOrderLineIsLinkedToItsOrder() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 1)));
        ArgumentCaptor<CustomerOrder> saved = ArgumentCaptor.forClass(CustomerOrder.class);

        service.placeOrder(EMAIL, null, com.rbctcsworld.ecommerce.TestAddresses.ADDRESS);

        verify(orders).save(saved.capture());
        assertThat(saved.getValue().getItems()).allSatisfy(item ->
                assertThat(ReflectionTestUtils.getField(item, "order")).as("JPA back-reference").isSameAs(saved.getValue()));
    }

    @Test
    void successfulPaymentChargesTheTotalAndMarksThePaidTime() {
        CustomerOrder order = new CustomerOrder("ORD-1", 7L);
        ReflectionTestUtils.setField(order, "id", 55L);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.of(2026, 10, 1, 9, 30));
        order.addItem(new OrderItem(1L, "SKU-M", "M", new BigDecimal("20.00"), 1));
        order.applyPricing(BigDecimal.ZERO, new BigDecimal("5.99"), new BigDecimal("1.20"), new BigDecimal("27.19"), null);
        when(orders.findByIdAndUserId(55L, 7L)).thenReturn(Optional.of(order));
        PayRequest card = new PayRequest("4242424242424242", 12, 2030, "123");

        OrderResponse r = service.pay(EMAIL, 55L, card);

        verify(payments).charge(55L, new BigDecimal("27.19"), card);
        verify(events).publishEvent(com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.of(55L, com.rbctcsworld.ecommerce.notification.OrderNotificationEvent.Type.PAYMENT_RECEIVED));
        verify(events).publishEvent(com.rbctcsworld.ecommerce.audit.AuditEvent.orderStatus(55L, "PLACED", "PAID"));
        assertThat(r.status()).isEqualTo(OrderStatus.PAID);
        assertThat(r.paidAt()).isNotNull();
        assertThat(r.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 30));
    }

    // ---- tax from the tax provider (store phase 3: Stripe Tax) ----

    @Test
    void theOrderTakesTheTaxProvidersTaxAndKeepsItsCalculationForTheRecord() {
        when(cartItems.findByUserIdOrderByIdAsc(7L))
                .thenReturn(List.of(new CartItem(7L, keyboard, 1), new CartItem(7L, mouse, 2)));
        org.mockito.Mockito.doReturn(new com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxQuote(new BigDecimal("7.77"), "taxcalc_1", false))
                .when(taxes).calculate(any());

        OrderResponse r = service.placeOrder(EMAIL, null, com.rbctcsworld.ecommerce.TestAddresses.ADDRESS);

        assertThat(r.tax()).isEqualByComparingTo("7.77");
        assertThat(r.total()).isEqualByComparingTo("97.77");             // 90.00 + free shipping + 7.77
        ArgumentCaptor<CustomerOrder> saved = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orders).save(saved.capture());
        assertThat(saved.getValue().getTaxCalculationId()).isEqualTo("taxcalc_1");

        ArgumentCaptor<com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxRequest> asked =
                ArgumentCaptor.forClass(com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxRequest.class);
        verify(taxes).calculate(asked.capture());
        ShippingAddress a = com.rbctcsworld.ecommerce.TestAddresses.ADDRESS.normalized();
        assertThat(asked.getValue().address()).isEqualTo(
                new com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxAddress(a.line1(), a.city(), a.state(), a.zip()));
        assertThat(asked.getValue().shipping()).isEqualByComparingTo("0.00");
        assertThat(asked.getValue().lines()).extracting(com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxLine::reference)
                .containsExactly("SKU-M", "SKU-K");
        assertThat(asked.getValue().lines()).extracting(com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxLine::quantity)
                .containsExactly(2, 1);
        assertThat(asked.getValue().merchandise()).isEqualByComparingTo("90.00");
    }

    @Test
    void theTaxProviderSeesTheLinesAfterTheDiscount() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, keyboard, 1)));
        when(coupons.validate(eq("save10"), eq(7L), any()))
                .thenReturn(new Coupon("SAVE10", Coupon.PERCENT, new BigDecimal("10"), null, null, null, null));

        service.placeOrder(EMAIL, "save10", com.rbctcsworld.ecommerce.TestAddresses.ADDRESS);

        ArgumentCaptor<com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxRequest> asked =
                ArgumentCaptor.forClass(com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxRequest.class);
        verify(taxes).calculate(asked.capture());
        assertThat(asked.getValue().merchandise()).isEqualByComparingTo("45.00");   // 50.00 - 10 %
        assertThat(asked.getValue().shipping()).isEqualByComparingTo("5.99");
    }

    @Test
    void whenTheTaxCannotBeCalculatedNothingIsSavedReservedOrCleared() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 1)));
        org.mockito.Mockito.doThrow(new com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException("down"))
                .when(taxes).calculate(any());

        assertThatThrownBy(() -> service.placeOrder(EMAIL, null, com.rbctcsworld.ecommerce.TestAddresses.ADDRESS))
                .isInstanceOf(com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException.class);
        verify(orders, never()).save(any());
        verifyNoInteractions(inventory);
        verify(cartItems, never()).deleteByUserId(any());
    }

    @Test
    void theFlatRateKeepsNoCalculation() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 1)));
        service.placeOrder(EMAIL, null, com.rbctcsworld.ecommerce.TestAddresses.ADDRESS);
        ArgumentCaptor<CustomerOrder> saved = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orders).save(saved.capture());
        assertThat(saved.getValue().getTaxCalculationId()).isNull();
    }

    @Test
    void aQuoteWithoutAnAddressSaysWhenTheTaxIsOnlyAnEstimate() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 1)));
        assertThat(service.quote(EMAIL, null, null).taxEstimated()).isFalse();          // flat rate: exact

        org.mockito.Mockito.doReturn(new com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxQuote(new BigDecimal("1.50"), null, true))
                .when(taxes).calculate(any());
        OrderDtos.QuoteResponse q = service.quote(EMAIL, null, null);
        assertThat(q.taxEstimated()).isTrue();
        assertThat(q.tax()).isEqualByComparingTo("1.50");
        assertThat(q.total()).isEqualByComparingTo("27.49");                             // 20.00 + 5.99 + 1.50
    }

    @Test
    void aQuoteWithAnAddressAsksForTheTaxOfThatAddress() {
        when(cartItems.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 3)));
        ShippingAddress typed = new ShippingAddress("Ada", " 12 Main St ", null, " Pittsburgh ", " pa ", " 15213 ", null);

        service.quote(EMAIL, null, typed);

        ArgumentCaptor<com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxRequest> asked =
                ArgumentCaptor.forClass(com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxRequest.class);
        verify(taxes).calculate(asked.capture());
        assertThat(asked.getValue().address()).isEqualTo(
                new com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxAddress("12 Main St", "Pittsburgh", "PA", "15213"));
        assertThat(asked.getValue().lines()).containsExactly(
                new com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxLine("SKU-M", new BigDecimal("60.00"), 3));
    }
}
