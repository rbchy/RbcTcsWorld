package com.rbctcsworld.ecommerce.payment;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.payment.PaymentDtos.AddCardRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The wallet of saved cards: REQ-PAY-08. */
@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    @Mock SavedPaymentMethodRepository repo;
    @Mock UserRepository users;
    private PaymentMethodService service;
    private final List<SavedPaymentMethod> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PaymentMethodService(repo, users, new MockPaymentGateway(), CLOCK);
        AppUser ada = new AppUser("ada@test.com", "x");
        ReflectionTestUtils.setField(ada, "id", 7L);
        lenient().when(users.findByEmail("ada@test.com")).thenReturn(Optional.of(ada));
        lenient().when(repo.findByUserIdOrderByIsDefaultDescCreatedAtDescIdDesc(7L)).thenAnswer(i -> List.copyOf(stored));
        lenient().when(repo.save(any())).thenAnswer(i -> {
            SavedPaymentMethod m = i.getArgument(0);
            if (m.getId() == null) ReflectionTestUtils.setField(m, "id", 100L + stored.size());
            if (!stored.contains(m)) stored.add(m);
            return m;
        });
    }

    private static AddCardRequest card(String number, int month, int year, boolean makeDefault) {
        return new AddCardRequest(number, month, year, makeDefault);
    }

    @Test
    void theFirstCardIsTheDefaultAndOnlyReceiptDataIsKept() {
        var r = service.add("ada@test.com", card("4242 4242 4242 4242", 12, 2030, false));
        assertThat(r.brand()).isEqualTo("Visa");
        assertThat(r.last4()).isEqualTo("4242");
        assertThat(r.isDefault()).isTrue();
        ArgumentCaptor<SavedPaymentMethod> saved = ArgumentCaptor.forClass(SavedPaymentMethod.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getToken()).startsWith("mock_pm_ok_").doesNotContain("4242");
        assertThat(r.toString()).doesNotContain("mock_pm");
    }

    @Test
    void aNewDefaultTakesOverFromTheOldOne() {
        service.add("ada@test.com", card("4242424242424242", 12, 2030, false));
        service.add("ada@test.com", card("5555555555554444", 12, 2030, true));
        assertThat(stored).extracting(SavedPaymentMethod::isDefault).containsExactly(false, true);
    }

    @Test
    void badExpiredAndUnusableCardsAreRefusedBeforeAnythingIsSaved() {
        assertThatThrownBy(() -> service.add("ada@test.com", card("4242424242424241", 12, 2030, false)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Invalid card number");
        assertThatThrownBy(() -> service.add("ada@test.com", card("4242424242424242", 5, 2026, false)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Card has expired");
        assertThat(service.add("ada@test.com", card("4242424242424242", 6, 2026, false)).expired()).isFalse();   // this month is still fine
        verify(repo, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void theSameCardTwiceIsRefused() {
        service.add("ada@test.com", card("4242424242424242", 12, 2030, false));
        assertThatThrownBy(() -> service.add("ada@test.com", card("4242 4242 4242 4242", 12, 2030, false)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already saved");
        service.add("ada@test.com", card("4242424242424242", 11, 2030, false));   // other expiry = other card
    }

    @Test
    void theWalletHoldsFiveCards() {
        String[] numbers = {"4242424242424242", "5555555555554444", "4000000000000002", "4000000000009995", "378282246310005"};
        for (String n : numbers) service.add("ada@test.com", card(n, 12, 2030, false));
        assertThatThrownBy(() -> service.add("ada@test.com", card("4000056655665556", 12, 2030, false)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("wallet is full");
    }

    @Test
    void aSavedDecliningCardKeepsDeclining() {
        service.add("ada@test.com", card(MockPaymentGateway.DECLINED_CARD, 12, 2030, false));
        String token = stored.get(0).getToken();
        assertThat(new MockPaymentGateway().chargeToken(1L, token, java.math.BigDecimal.TEN).approved()).isFalse();
    }

    @Test
    void deletingTheDefaultPromotesTheNewestRemainingCard() {
        service.add("ada@test.com", card("4242424242424242", 12, 2030, false));
        service.add("ada@test.com", card("5555555555554444", 12, 2030, false));
        SavedPaymentMethod first = stored.get(0);
        SavedPaymentMethod second = stored.get(1);
        when(repo.findByIdAndUserId(first.getId(), 7L)).thenReturn(Optional.of(first));
        org.mockito.Mockito.doAnswer(i -> stored.remove(i.<SavedPaymentMethod>getArgument(0))).when(repo).delete(first);
        service.delete("ada@test.com", first.getId());
        assertThat(stored).containsExactly(second);
        assertThat(second.isDefault()).isTrue();
    }

    @Test
    void somebodyElsesCardDoesNotExist() {
        when(repo.findByIdAndUserId(55L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ownedBy("ada@test.com", 55L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete("ada@test.com", 55L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.makeDefault("ada@test.com", 55L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void saveAfterPaymentNeverFailsThePayment() {
        String[] numbers = {"4242424242424242", "5555555555554444", "4000000000000002", "4000000000009995", "378282246310005"};
        for (String n : numbers) service.add("ada@test.com", card(n, 12, 2030, false));
        service.saveAfterPayment("ada@test.com", "4000056655665556", 12, 2030);      // full wallet: skipped silently
        service.saveAfterPayment("ada@test.com", "4242424242424242", 12, 2030);      // twin: skipped silently
        assertThat(stored).hasSize(5);
    }

    @Test
    void brandsFollowTheFirstDigits() {
        assertThat(PaymentMethodService.brand("4242424242424242")).isEqualTo("Visa");
        assertThat(PaymentMethodService.brand("5555555555554444")).isEqualTo("Mastercard");
        assertThat(PaymentMethodService.brand("2221000000000009")).isEqualTo("Mastercard");
        assertThat(PaymentMethodService.brand("378282246310005")).isEqualTo("Amex");
        assertThat(PaymentMethodService.brand("6011111111111117")).isEqualTo("Discover");
        assertThat(PaymentMethodService.brand("9999999999999995")).isEqualTo("Card");
    }

    @Test
    void anExpiredSavedCardIsMarkedExpired() {
        SavedPaymentMethod m = new SavedPaymentMethod(7L, "Visa", "4242", 5, 2026, "mock", "mock_pm_ok_x");
        ReflectionTestUtils.setField(m, "id", 1L);
        stored.add(m);
        assertThat(service.list("ada@test.com")).singleElement().satisfies(r -> assertThat(r.expired()).isTrue());
        verify(repo, never()).delete(any());
    }
}
