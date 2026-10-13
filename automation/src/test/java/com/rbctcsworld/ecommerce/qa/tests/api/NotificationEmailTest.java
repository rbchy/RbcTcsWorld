package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.AdminClient;
import com.rbctcsworld.ecommerce.qa.api.MailpitClient;
import com.rbctcsworld.ecommerce.qa.api.MailpitClient.Mail;
import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Order e-mails end to end: API action -> outbox -> SMTP -> Mailpit inbox. In CI two backends share the
 * outbox, so "exactly one e-mail" also proves that two instances never send the same row.
 * Skipped when no Mailpit is running (-DmailpitUrl).
 */
@Tag("api")
@Tag("notification")
@Epic("Orders")
@Feature("E-mail notifications")
class NotificationEmailTest {

    private final MailpitClient mailpit = new MailpitClient();
    private final AdminClient admin = new AdminClient();
    private final OrderClient orders = new OrderClient();

    @BeforeEach
    void mailServer() {
        assumeTrue(mailpit.isAvailable(), "Mailpit is not running (-DmailpitUrl)");
    }

    private static List<String> subjects(List<Mail> inbox) {
        return inbox.stream().map(Mail::subject).toList();
    }

    @Test
    @DisplayName("Paid order: exactly one 'received' and one 'payment' e-mail, to the customer only")
    void orderAndPaymentEmails() {
        Fixtures.Customer c = Fixtures.newCustomer();
        long product = Fixtures.product("12.50", 10);
        long order = Fixtures.paidOrder(c, product, 2);
        String number = orders.get(c.token(), order).then().statusCode(200).extract().path("orderNumber");

        List<Mail> inbox = mailpit.waitForInbox(c.email(), m -> m.size() >= 2);

        assertEquals(java.util.Set.of(
                        "Order " + number + " received - $32.49",              // 25.00 + 5.99 shipping + 1.50 tax
                        "Payment received for order " + number),
                java.util.Set.copyOf(subjects(inbox)));
        assertEquals(2, inbox.size(), "exactly one of each, no duplicates: " + subjects(inbox));
        String received = inbox.stream().filter(m -> m.subject().contains("received -")).findFirst().orElseThrow().id();
        String text = mailpit.text(received);
        assertTrue(text.contains("2 x") && text.contains("Total $32.49"), text);
    }

    @Test
    @DisplayName("Shipping e-mail carries the same tracking number as the API")
    void shippedEmailHasTheTrackingNumber() {
        Fixtures.Customer c = Fixtures.newCustomer();
        long order = Fixtures.shippedOrder(c, Fixtures.product("60.00", 5), 1);
        String tracking = orders.get(c.token(), order).then().extract().path("trackingNumber");

        List<Mail> inbox = mailpit.waitForInbox(c.email(), m -> subjects(m).stream().anyMatch(s -> s.endsWith("is on its way")));

        Mail shipped = inbox.stream().filter(m -> m.subject().endsWith("is on its way")).findFirst().orElseThrow();
        String text = mailpit.text(shipped.id());
        assertTrue(text.contains("Tracking number: " + tracking), text);
    }

    @Test
    @DisplayName("Admin sees every e-mail of an order as SENT; re-sending a sent e-mail is refused")
    void adminSeesTheOutbox() {
        Fixtures.Customer c = Fixtures.newCustomer();
        long order = Fixtures.placedOrder(c, Fixtures.product("5.00", 5), 1);
        mailpit.waitForInbox(c.email(), m -> !m.isEmpty());

        // the mail server accepts the e-mail a moment before the backend records SENT: wait for the record
        List<String> status = List.of();
        for (int i = 0; i < 40 && !status.equals(List.of("SENT")); i++) {
            status = admin.notifications(Fixtures.adminToken(), order).then().statusCode(200)
                    .extract().jsonPath().getList("status");
            if (!status.equals(List.of("SENT"))) sleep();
        }
        assertEquals(List.of("SENT"), status);
        int id = admin.notifications(Fixtures.adminToken(), order).then().extract().jsonPath().<Integer>getList("id").get(0);
        admin.retryNotification(Fixtures.adminToken(), id).then().statusCode(409);
        admin.notifications(c.token(), order).then().statusCode(403);
    }

    @Test
    @DisplayName("A declined card sends no payment e-mail")
    void declinedCardNoPaymentEmail() {
        Fixtures.Customer c = Fixtures.newCustomer();
        long order = Fixtures.placedOrder(c, Fixtures.product("5.00", 5), 1);
        orders.pay(c.token(), order, TestData.CARD_DECLINED).then().statusCode(402);

        List<Mail> inbox = mailpit.waitForInbox(c.email(), m -> !m.isEmpty());
        assertEquals(1, inbox.size(), subjects(inbox).toString());
        assertTrue(inbox.get(0).subject().contains("received"), inbox.get(0).subject());
    }

    private static void sleep() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
