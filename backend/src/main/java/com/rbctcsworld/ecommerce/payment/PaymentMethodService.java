package com.rbctcsworld.ecommerce.payment;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.payment.PaymentDtos.AddCardRequest;
import com.rbctcsworld.ecommerce.payment.PaymentDtos.MethodResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * The customer's wallet of saved cards. Rules:
 *  - what is kept: brand, last 4, expiry and the provider's token - never the number or the CVV;
 *  - at most {@value #MAX} cards (409), the same card twice is refused (409);
 *  - the first card is the default; makeDefault moves it; deleting the default makes the newest remaining card default;
 *  - somebody else's card does not exist for you (404);
 *  - a malformed, Luhn-invalid or expired card is 400 and a card the provider cannot use is 400.
 */
@Service
@Transactional
public class PaymentMethodService {

    public static final int MAX = 5;

    private final SavedPaymentMethodRepository repo;
    private final UserRepository users;
    private final PaymentGateway gateway;
    private final Clock clock;

    public PaymentMethodService(SavedPaymentMethodRepository repo, UserRepository users, PaymentGateway gateway, Clock clock) {
        this.repo = repo;
        this.users = users;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MethodResponse> list(String email) {
        YearMonth now = YearMonth.now(clock);
        return mine(user(email).getId()).stream().map(m -> MethodResponse.from(m, now)).toList();
    }

    public MethodResponse add(String email, AddCardRequest r) {
        Long userId = user(email).getId();
        return MethodResponse.from(save(userId, r.cardNumber(), r.expiryMonth(), r.expiryYear(), r.makeDefault()), YearMonth.now(clock));
    }

    /** "Save this card" after a payment went through: never fails the payment, a full wallet or a twin is simply skipped. */
    public void saveAfterPayment(String email, String cardNumber, int month, int year) {
        try {
            save(user(email).getId(), cardNumber, month, year, false);
        } catch (ConflictException | BusinessRuleException ignored) {
            // wallet full, card already saved, or not storable at the provider: the payment itself succeeded
        }
    }

    private SavedPaymentMethod save(Long userId, String rawNumber, int month, int year, boolean makeDefault) {
        if (!gateway.acceptsCardNumbers()) {
            throw new BusinessRuleException(PaymentService.SECURE_FORM_ONLY);
        }
        String number = rawNumber.replace(" ", "");
        if (number.length() < 13 || number.length() > 19 || !MockPaymentGateway.luhnValid(number)) {
            throw new BusinessRuleException("Invalid card number");
        }
        if (YearMonth.of(year, month).isBefore(YearMonth.now(clock))) {
            throw new BusinessRuleException("Card has expired");
        }
        List<SavedPaymentMethod> all = mine(userId);
        if (all.size() >= MAX) throw new ConflictException("Your wallet is full (" + MAX + " cards). Delete one first.");
        String last4 = number.substring(number.length() - 4);
        String brand = brand(number);
        for (SavedPaymentMethod m : all) {
            if (m.getLast4().equals(last4) && m.getBrand().equals(brand) && m.getExpiryMonth() == month && m.getExpiryYear() == year) {
                throw new ConflictException("This card is already saved");
            }
        }
        String token = gateway.tokenize(number);
        if (token == null) throw new BusinessRuleException("This card cannot be saved. In test mode use a Stripe test card, e.g. 4242 4242 4242 4242");
        boolean first = all.isEmpty();
        if (first || makeDefault) clearDefault(all);
        SavedPaymentMethod saved = new SavedPaymentMethod(userId, brand, last4, month, year, gateway.name(), token);
        saved.setDefault(first || makeDefault);
        return repo.save(saved);
    }

    public MethodResponse makeDefault(String email, long id) {
        Long userId = user(email).getId();
        SavedPaymentMethod m = owned(id, userId);
        clearDefault(mine(userId));
        m.setDefault(true);
        return MethodResponse.from(repo.save(m), YearMonth.now(clock));
    }

    public void delete(String email, long id) {
        Long userId = user(email).getId();
        SavedPaymentMethod m = owned(id, userId);
        boolean wasDefault = m.isDefault();
        repo.delete(m);
        repo.flush();
        if (wasDefault) {
            mine(userId).stream().max(Comparator.comparing(SavedPaymentMethod::getCreatedAt).thenComparing(SavedPaymentMethod::getId))
                    .ifPresent(next -> { next.setDefault(true); repo.save(next); });
        }
    }

    /** A card of this customer, for paying; 404 for anyone else's. */
    @Transactional(readOnly = true)
    public SavedPaymentMethod ownedBy(String email, long id) {
        return owned(id, user(email).getId());
    }

    /** Visa 4, Mastercard 51-55 and 2221-2720, American Express 34/37, Discover 6011/65; "Card" otherwise. */
    static String brand(String n) {
        if (n.startsWith("4")) return "Visa";
        if (n.startsWith("34") || n.startsWith("37")) return "Amex";
        if (n.length() >= 4) {
            int p4 = Integer.parseInt(n.substring(0, 4));
            int p2 = Integer.parseInt(n.substring(0, 2));
            if ((p2 >= 51 && p2 <= 55) || (p4 >= 2221 && p4 <= 2720)) return "Mastercard";
            if (p4 == 6011 || p2 == 65) return "Discover";
        }
        return "Card";
    }

    private void clearDefault(List<SavedPaymentMethod> all) {
        for (SavedPaymentMethod m : all) {
            if (m.isDefault()) {
                m.setDefault(false);
                repo.save(m);
            }
        }
        repo.flush();
    }

    private List<SavedPaymentMethod> mine(Long userId) {
        return repo.findByUserIdOrderByIsDefaultDescCreatedAtDescIdDesc(userId);
    }

    private SavedPaymentMethod owned(long id, Long userId) {
        return repo.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Saved card not found: " + id));
    }

    private AppUser user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
