package com.rbctcsworld.ecommerce.contact;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.common.exception.TooManyRequestsException;
import com.rbctcsworld.ecommerce.notification.AccountEmailEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REQ-CS-01..03: the contact form's rules (ADR-0018). */
class ContactServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 15, 0);

    private final ContactMessageRepository messages = mock(ContactMessageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ContactService service = service("support@shop.test");

    private ContactService service(String contactEmail) {
        return new ContactService(messages, users, events, CLOCK, " Test Shop ", contactEmail, "owner@shop.test", 2);
    }

    private static ContactRequest request(String email, String message, String website) {
        return new ContactRequest(" Ada Lovelace ", email, "ORDER", " ord-123 ", message, website);
    }

    private ContactMessage saved() {
        ArgumentCaptor<ContactMessage> m = ArgumentCaptor.forClass(ContactMessage.class);
        verify(messages).save(m.capture());
        return m.getValue();
    }

    private List<AccountEmailEvent> mails() {
        ArgumentCaptor<AccountEmailEvent> e = ArgumentCaptor.forClass(AccountEmailEvent.class);
        verify(events, times(2)).publishEvent(e.capture());
        return e.getAllValues();
    }

    @Test
    void aVisitorsMessageIsSavedTidiedAndAnsweredWithATicket() {
        when(messages.save(any())).thenAnswer(call -> call.getArgument(0));
        ContactService.Sent sent = service.send(request(" Ada@Example.COM ", "  Where is my parcel, please?  ", ""), null);

        ContactMessage m = saved();
        assertThat(sent.ticket()).matches("MSG-[A-HJKMNP-Z2-9]{6}").isEqualTo(m.getTicket());
        assertThat(sent.replyDays()).isEqualTo(2);
        assertThat(m.getName()).isEqualTo("Ada Lovelace");
        assertThat(m.getEmail()).isEqualTo("ada@example.com");
        assertThat(m.getUserId()).isNull();
        assertThat(m.getOrderNumber()).isEqualTo("ORD-123");
        assertThat(m.getMessage()).isEqualTo("Where is my parcel, please?");
        assertThat(m.getStatus()).isEqualTo(ContactMessage.NEW);
        assertThat(m.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void theSenderGetsATicketButNeverTheirOwnTextAndTheOwnerGetsTheMessage() {
        when(messages.save(any())).thenAnswer(call -> call.getArgument(0));
        service.send(request("ada@example.com", "Visit http://spam.example for cheap pills", null), null);
        List<AccountEmailEvent> mails = mails();

        AccountEmailEvent receipt = mails.get(0);
        assertThat(receipt.recipient()).isEqualTo("ada@example.com");
        assertThat(receipt.type()).isEqualTo(AccountEmailEvent.Type.CONTACT_RECEIVED);
        assertThat(receipt.subject()).startsWith("We received your message MSG-");
        assertThat(receipt.body()).contains("Test Shop", "within 2 business days", "If you did not write to us")
                .doesNotContain("spam.example");

        AccountEmailEvent toOwner = mails.get(1);
        assertThat(toOwner.recipient()).isEqualTo("support@shop.test");
        assertThat(toOwner.type()).isEqualTo(AccountEmailEvent.Type.CONTACT_NEW);
        assertThat(toOwner.subject()).contains("(ORDER)", "ada@example.com");
        assertThat(toOwner.body()).contains("Ada Lovelace <ada@example.com> (not logged in)", "Order: ORD-123", "spam.example");
    }

    @Test
    void withoutAContactAddressTheMessagesGoToTheAdmin() {
        when(messages.save(any())).thenAnswer(call -> call.getArgument(0));
        service("  ").send(request("ada@example.com", "Hello, a question about sizes", null), null);
        assertThat(mails().get(1).recipient()).isEqualTo("owner@shop.test");
    }

    @Test
    void aLoggedInCustomerWritesWithTheAccountsOwnAddress() {
        AppUser user = mock(AppUser.class);
        when(user.getEmail()).thenReturn("customer@shop.test");
        when(user.getId()).thenReturn(42L);
        when(users.findByEmail("customer@shop.test")).thenReturn(Optional.of(user));
        when(messages.save(any())).thenAnswer(call -> call.getArgument(0));

        service.send(request("someone-else@example.com", "My order arrived broken", null), "customer@shop.test");

        ContactMessage m = saved();
        assertThat(m.getEmail()).isEqualTo("customer@shop.test");
        assertThat(m.getUserId()).isEqualTo(42L);
        assertThat(mails().get(1).body()).contains("(customer account)");
    }

    @Test
    void aFilledHoneypotLooksAcceptedButNothingIsSavedOrSent() {
        ContactService.Sent sent = service.send(request("bot@example.com", "Buy followers cheap now!!", "http://x"), null);
        assertThat(sent.ticket()).startsWith("MSG-");
        verify(messages, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aVisitorMustGiveAnAddress(String email) {
        assertThatThrownBy(() -> service.send(request(email, "A long enough message", null), null))
                .isInstanceOf(BusinessRuleException.class).hasMessage(ContactService.EMAIL_NEEDED);
        assertThatThrownBy(() -> service.send(request(null, "A long enough message", null), null))
                .isInstanceOf(BusinessRuleException.class);
        verify(messages, never()).save(any());
    }

    @Test
    void spacesDoNotCountTowardsTheTenCharacters() {
        assertThatThrownBy(() -> service.send(request("a@b.co", "  short   ", null), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("at least 10");
    }

    @Test
    void threeMessagesPerAddressAndHourThenPleaseWait() {
        when(messages.countByEmailAndCreatedAtAfter("ada@example.com", NOW.minusHours(1))).thenReturn(3L);
        assertThatThrownBy(() -> service.send(request("ada@example.com", "A fourth message this hour", null), null))
                .isInstanceOf(TooManyRequestsException.class).hasMessage(ContactService.TOO_MANY)
                .extracting(e -> ((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(3600L);
        when(messages.countByEmailAndCreatedAtAfter("ada@example.com", NOW.minusHours(1))).thenReturn(2L);
        when(messages.save(any())).thenAnswer(call -> call.getArgument(0));
        service.send(request("ada@example.com", "The third message is fine", null), null);
        verify(messages).save(any());
    }

    @Test
    void theWholeShopTakesAtMost200MessagesAnHour() {
        when(messages.countByCreatedAtAfter(NOW.minusHours(1))).thenReturn(200L);
        assertThatThrownBy(() -> service.send(request("ada@example.com", "Hello there shop", null), null))
                .isInstanceOf(TooManyRequestsException.class).hasMessage(ContactService.BUSY);
        when(messages.countByCreatedAtAfter(NOW.minusHours(1))).thenReturn(199L);
        when(messages.save(any())).thenAnswer(call -> call.getArgument(0));
        service.send(request("ada@example.com", "Hello there shop", null), null);
    }

    @Test
    void aTicketNumberIsNeverUsedTwice() {
        when(messages.existsByTicket(anyString())).thenReturn(true, true, false);
        assertThat(service.newTicket()).startsWith("MSG-");
        verify(messages, times(3)).existsByTicket(anyString());
        when(messages.existsByTicket(anyString())).thenReturn(true);
        assertThatThrownBy(service::newTicket).isInstanceOf(IllegalStateException.class);
    }

    private ContactMessage stored() {
        ContactMessage m = new ContactMessage("MSG-ABCDEF", "Ada", "ada@example.com", null, "RETURN", null,
                "Can I return the mug?", NOW.minusDays(1));
        when(messages.findById(7L)).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void theOwnersAnswerIsSavedAndEmailedWithTheQuestionQuoted() {
        ContactMessage m = stored();
        service.reply(7L, "  Yes, within 30 days.  ", "admin@shop.test");

        assertThat(m.getStatus()).isEqualTo(ContactMessage.ANSWERED);
        assertThat(m.getReply()).isEqualTo("Yes, within 30 days.");
        assertThat(m.getAnsweredBy()).isEqualTo("admin@shop.test");
        assertThat(m.getAnsweredAt()).isEqualTo(NOW);
        ArgumentCaptor<AccountEmailEvent> e = ArgumentCaptor.forClass(AccountEmailEvent.class);
        verify(events).publishEvent(e.capture());
        assertThat(e.getValue().recipient()).isEqualTo("ada@example.com");
        assertThat(e.getValue().type()).isEqualTo(AccountEmailEvent.Type.CONTACT_REPLY);
        assertThat(e.getValue().subject()).isEqualTo("Re: your message MSG-ABCDEF");
        assertThat(e.getValue().body()).contains("Hello Ada", "Yes, within 30 days.", "Test Shop", "Can I return the mug?");
    }

    @Test
    void anAnswerIsGivenOnceAndNotToAClosedMessage() {
        ContactMessage m = stored();
        assertThatThrownBy(() -> service.reply(7L, " ", "a")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.reply(7L, null, "a")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.reply(7L, "x".repeat(4001), "a")).isInstanceOf(BusinessRuleException.class);
        service.reply(7L, "OK", "a");
        assertThatThrownBy(() -> service.reply(7L, "Again", "a")).isInstanceOf(ConflictException.class);
        service.close(7L);
        assertThat(m.getStatus()).isEqualTo(ContactMessage.CLOSED);
        assertThatThrownBy(() -> service.close(7L)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.reply(8L, "Hello", "a")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theOwnerListsAllOrOneStatusNewestFirst() {
        service.list(null);
        verify(messages).findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, ContactService.LIST_LIMIT));
        service.list(" new ");
        verify(messages).findByStatusOrderByCreatedAtDescIdDesc(eq("NEW"), any());
        assertThatThrownBy(() -> service.list("SPAM")).isInstanceOf(BusinessRuleException.class);
        assertThat(service.info().replyDays()).isEqualTo(2);
        assertThat(service.info().topics()).containsExactly("ORDER", "RETURN", "PRODUCT", "PAYMENT", "OTHER");
    }
}
