package com.rbctcsworld.ecommerce.cart;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.cart.CartDtos.CartResponse;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.product.Product;
import com.rbctcsworld.ecommerce.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final String EMAIL = "buyer@test.com";

    @Mock CartItemRepository items;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @InjectMocks CartService service;

    private AppUser user;
    private Product mouse;

    @BeforeEach
    void setUp() {
        user = new AppUser(EMAIL, "hash");
        ReflectionTestUtils.setField(user, "id", 7L);
        mouse = new Product("Mouse", "SKU-M", "electronics", new BigDecimal("20.00"), 5);
        ReflectionTestUtils.setField(mouse, "id", 1L);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void addNewProductCreatesLine() {
        when(products.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(mouse));
        when(items.findByUserIdAndProductId(7L, 1L)).thenReturn(Optional.empty());
        when(items.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 2)));

        CartResponse cart = service.addItem(EMAIL, 1L, 2);

        ArgumentCaptor<CartItem> saved = ArgumentCaptor.forClass(CartItem.class);
        verify(items).save(saved.capture());
        assertThat(saved.getValue().getQuantity()).isEqualTo(2);
        assertThat(cart.totalQuantity()).isEqualTo(2);
        assertThat(cart.subtotal()).isEqualByComparingTo("40.00");
    }

    @Test
    void addingSameProductAgainMergesQuantity() {
        CartItem existing = new CartItem(7L, mouse, 2);
        when(products.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(mouse));
        when(items.findByUserIdAndProductId(7L, 1L)).thenReturn(Optional.of(existing));
        when(items.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(existing));

        service.addItem(EMAIL, 1L, 3);

        assertThat(existing.getQuantity()).isEqualTo(5);
    }

    @Test
    void addMoreThanStockIsConflict() {
        when(products.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(mouse));
        when(items.findByUserIdAndProductId(7L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(EMAIL, 1L, 6))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("available 5");
        verify(items, never()).save(any());
    }

    @Test
    void mergedQuantityAboveLimitIsRejected() {
        mouse.setStock(100);
        when(products.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(mouse));
        when(items.findByUserIdAndProductId(7L, 1L)).thenReturn(Optional.of(new CartItem(7L, mouse, 8)));

        assertThatThrownBy(() -> service.addItem(EMAIL, 1L, 5))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void addInactiveOrUnknownProductIsNotFound() {
        when(products.findByIdAndActiveTrue(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(EMAIL, 42L, 1)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatingAnotherCustomersItemIsNotFound() {
        when(items.findByIdAndUserId(500L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateItem(EMAIL, 500L, 1)).isInstanceOf(NotFoundException.class);
        verify(items, never()).save(any());
    }

    /** REQ-AFF-02: an affiliate product is bought at the partner shop, never in the cart (ADR-0020). */
    @Test
    void aPartnerProductCannotBeAddedAndAnOldLineShowsUnavailable() {
        mouse.changeAffiliateUrl(" https://www.amazon.com/dp/B000TEST?tag=shop-20 ");
        when(products.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(mouse));

        assertThatThrownBy(() -> service.addItem(EMAIL, 1L, 1))
                .isInstanceOf(BusinessRuleException.class).hasMessage(CartService.PARTNER_ONLY);
        verify(items, never()).save(any());

        when(items.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(new CartItem(7L, mouse, 1)));
        assertThat(service.getCart(EMAIL).items()).singleElement().satisfies(l -> assertThat(l.available()).isFalse());
        assertThat(mouse.getAffiliateUrl()).isEqualTo("https://www.amazon.com/dp/B000TEST?tag=shop-20");
        mouse.changeAffiliateUrl("  ");
        assertThat(mouse.soldByPartner()).isFalse();
    }

    @Test
    void lineIsUnavailableWhenStockDropsBelowCartQuantity() {
        CartItem line = new CartItem(7L, mouse, 4);
        mouse.setStock(2);
        when(items.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(line));

        CartResponse cart = service.getCart(EMAIL);

        assertThat(cart.items()).singleElement().satisfies(l -> assertThat(l.available()).isFalse());
    }

    // ---- added after mutation testing (PIT): updateItem/removeItem/clear had no unit test, and the
    // ---- 1..10 quantity boundaries, the inactive-product check and the "available" flag survived mutation.

    private CartItem line(int quantity) {
        CartItem line = new CartItem(7L, mouse, quantity);
        ReflectionTestUtils.setField(line, "id", 500L);
        when(items.findByIdAndUserId(500L, 7L)).thenReturn(Optional.of(line));
        return line;
    }

    @Test
    void updatedQuantityMustBeBetween1And10() {
        mouse.setStock(100);
        CartItem line = line(2);
        when(items.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(line));

        assertThatThrownBy(() -> service.updateItem(EMAIL, 500L, 0)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.updateItem(EMAIL, 500L, CartService.MAX_QTY_PER_ITEM + 1))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("between 1 and 10");
        assertThat(service.updateItem(EMAIL, 500L, 1).totalQuantity()).isEqualTo(1);
        assertThat(service.updateItem(EMAIL, 500L, CartService.MAX_QTY_PER_ITEM).totalQuantity()).isEqualTo(10);
        verify(items, times(2)).save(line);
    }

    @Test
    void updatedQuantityMayUseTheLastUnitButNotMore() {
        CartItem line = line(1);                                         // stock 5
        when(items.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(line));

        CartResponse cart = service.updateItem(EMAIL, 500L, 5);
        assertThat(cart.totalQuantity()).isEqualTo(5);
        assertThat(cart.subtotal()).isEqualByComparingTo("100.00");
        assertThat(cart.items()).singleElement().satisfies(l -> assertThat(l.available()).isTrue());

        assertThatThrownBy(() -> service.updateItem(EMAIL, 500L, 6))
                .isInstanceOf(ConflictException.class).hasMessageContaining("available 5");
    }

    @Test
    void productTakenOffSaleCannotBeUpdatedAndShowsUnavailable() {
        CartItem line = line(1);
        mouse.setActive(false);

        assertThatThrownBy(() -> service.updateItem(EMAIL, 500L, 2)).isInstanceOf(NotFoundException.class);
        assertThat(line.getQuantity()).isEqualTo(1);
        verify(items, never()).save(any());

        when(items.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(line));
        assertThat(service.getCart(EMAIL).items()).singleElement().satisfies(l -> assertThat(l.available()).isFalse());
    }

    @Test
    void removeDeletesTheOwnedLineAndClearEmptiesTheCart() {
        CartItem line = line(2);
        when(items.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of());

        CartResponse cart = service.removeItem(EMAIL, 500L);

        verify(items).delete(line);
        assertThat(cart.items()).isEmpty();
        assertThat(cart.subtotal()).isEqualByComparingTo("0");

        service.clear(EMAIL);
        verify(items).deleteByUserId(7L);
    }
}
