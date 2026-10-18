package com.rbctcsworld.ecommerce.payment;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.PaymentDeclinedException;
import com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException;
import com.rbctcsworld.ecommerce.payment.PaymentGateway.GatewayResult;
import com.rbctcsworld.ecommerce.payment.PaymentDtos.PayRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

/**
 * Charges and refunds through the gateway and records every attempt.
 * Runs inside the caller's (OrderService) transaction.
 *
 * noRollbackFor: a declined card throws PaymentDeclinedException (-> HTTP 402), but the FAILED
 * transaction row must still be committed for audit/fraud analysis. Without this setting the
 * exception would mark the whole transaction rollback-only and the failed attempt would vanish.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = PaymentDeclinedException.class)
public class PaymentService {

    private final PaymentGateway gateway;
    private final PaymentTransactionRepository transactions;
    private final Clock clock;

    public PaymentService(PaymentGateway gateway, PaymentTransactionRepository transactions, Clock clock) {
        this.gateway = gateway;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * 400 for malformed/expired card (nothing recorded - the card never reached the gateway),
     * 402 for a declined card (FAILED transaction IS recorded), 503 when the provider cannot be reached
     * (FAILED "Provider unavailable" recorded), otherwise a SUCCEEDED transaction with the provider's id.
     */
    public PaymentTransaction charge(Long orderId, BigDecimal amount, PayRequest card) {
        String number = card.cardNumber().replace(" ", "");
        if (number.length() < 13 || number.length() > 19 || !MockPaymentGateway.luhnValid(number)) {
            throw new BusinessRuleException("Invalid card number");
        }
        if (YearMonth.of(card.expiryYear(), card.expiryMonth()).isBefore(YearMonth.now(clock))) {
            throw new BusinessRuleException("Card has expired");
        }
        String last4 = number.substring(number.length() - 4);
        return settle(orderId, amount, last4, () -> gateway.charge(orderId, number, amount));
    }

    /**
     * Charges a saved card by its provider token. An expired card is 400 and nothing is recorded; everything else
     * behaves like {@link #charge}.
     */
    public PaymentTransaction chargeSaved(Long orderId, BigDecimal amount, SavedPaymentMethod method) {
        if (method.expiredAt(YearMonth.now(clock))) {
            throw new BusinessRuleException("Card has expired");
        }
        return settle(orderId, amount, method.getLast4(), () -> gateway.chargeToken(orderId, method.getToken(), amount));
    }

    private PaymentTransaction settle(Long orderId, BigDecimal amount, String last4, java.util.function.Supplier<GatewayResult> call) {
        GatewayResult result;
        try {
            result = call.get();
        } catch (PaymentUnavailableException e) {
            transactions.save(new PaymentTransaction(orderId, PaymentTransaction.CHARGE, PaymentTransaction.FAILED,
                    amount, last4, "Provider unavailable", gateway.name(), null));
            throw e;
        }
        if (!result.approved()) {
            transactions.save(new PaymentTransaction(orderId, PaymentTransaction.CHARGE, PaymentTransaction.FAILED,
                    amount, last4, result.reason(), gateway.name(), result.reference()));
            throw new PaymentDeclinedException("Payment declined: " + result.reason());
        }
        return transactions.save(new PaymentTransaction(orderId, PaymentTransaction.CHARGE,
                PaymentTransaction.SUCCEEDED, amount, last4, null, gateway.name(), result.reference()));
    }

    /**
     * Refunds an amount of the order's successful charge (cancelled order: all of it; return: the refund
     * the return policy allows). A provider that cannot refund -> 503, and the caller's transaction rolls back.
     */
    public PaymentTransaction refund(Long orderId, BigDecimal amount) {
        List<PaymentTransaction> history = transactions.findByOrderIdOrderByIdAsc(orderId);
        PaymentTransaction charge = history.stream()
                .filter(t -> PaymentTransaction.CHARGE.equals(t.getType()) && PaymentTransaction.SUCCEEDED.equals(t.getStatus()))
                .findFirst().orElse(null);
        int refundNumber = 1 + (int) history.stream()
                .filter(t -> PaymentTransaction.REFUND.equals(t.getType()) && PaymentTransaction.SUCCEEDED.equals(t.getStatus()))
                .count();
        GatewayResult result = gateway.refund(orderId, charge == null ? null : charge.getProviderReference(), amount, refundNumber);
        if (!result.approved()) {
            throw new PaymentUnavailableException("Refund was not accepted: " + result.reason());
        }
        return transactions.save(new PaymentTransaction(orderId, PaymentTransaction.REFUND, PaymentTransaction.SUCCEEDED,
                amount, charge == null ? null : charge.getCardLast4(), null, gateway.name(), result.reference()));
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<PaymentTransaction> history(Long orderId) {
        return transactions.findByOrderIdOrderByIdAsc(orderId);
    }
}
