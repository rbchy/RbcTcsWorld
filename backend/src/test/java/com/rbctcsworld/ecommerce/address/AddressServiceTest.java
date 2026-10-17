package com.rbctcsworld.ecommerce.address;

import com.rbctcsworld.ecommerce.address.AddressDtos.AddressRequest;
import com.rbctcsworld.ecommerce.address.AddressDtos.AddressResponse;
import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.order.ShippingAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The address book: REQ-ADR-01..03. The repository is replaced by an in-memory list, so the rules (default,
 * duplicates, limit, ownership) are tested on real data, not on stubbed answers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressServiceTest {

    @Mock CustomerAddressRepository repo;
    @Mock UserRepository users;
    @InjectMocks AddressService service;

    private final List<CustomerAddress> db = new ArrayList<>();
    private long nextId = 1;
    private LocalDateTime clock = LocalDateTime.of(2026, 10, 17, 9, 0);

    private static AppUser user(long id, String email) {
        AppUser u = new AppUser(email, "x");
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @BeforeEach
    void inMemoryRepository() {
        when(users.findByEmail("ada@test.com")).thenReturn(Optional.of(user(1, "ada@test.com")));
        when(users.findByEmail("bob@test.com")).thenReturn(Optional.of(user(2, "bob@test.com")));
        when(repo.save(any(CustomerAddress.class))).thenAnswer(inv -> {
            CustomerAddress a = inv.getArgument(0);
            if (a.getId() == null) {
                ReflectionTestUtils.setField(a, "id", nextId++);
                ReflectionTestUtils.setField(a, "createdAt", clock = clock.plusMinutes(1));
                db.add(a);
            }
            return a;
        });
        when(repo.findByUserIdOrderByIsDefaultDescCreatedAtDescIdDesc(anyLong())).thenAnswer(inv -> db.stream()
                .filter(a -> a.getUserId().equals(inv.getArgument(0)))
                .sorted(Comparator.comparing(CustomerAddress::isDefault).reversed()
                        .thenComparing(CustomerAddress::getCreatedAt, Comparator.reverseOrder()))
                .toList());
        when(repo.findByIdAndUserId(anyLong(), anyLong())).thenAnswer(inv -> db.stream()
                .filter(a -> a.getId().equals(inv.getArgument(0)) && a.getUserId().equals(inv.getArgument(1))).findFirst());
        org.mockito.Mockito.doAnswer(inv -> db.remove((CustomerAddress) inv.getArgument(0))).when(repo).delete(any(CustomerAddress.class));
    }

    private static ShippingAddress at(String line1) {
        return new ShippingAddress("Ada Lovelace", line1, null, "Pittsburgh", "pa", " 15213 ", "412-555-0100");
    }

    private AddressResponse add(String email, String label, String line1, boolean makeDefault) {
        return service.add(email, new AddressRequest(label, at(line1), makeDefault));
    }

    @Test
    void theFirstAddressIsTheDefaultAndIsStoredTidy() {
        AddressResponse home = add("ada@test.com", "  Home  ", "1 Main St", false);
        assertThat(home.isDefault()).isTrue();
        assertThat(home.label()).isEqualTo("Home");
        assertThat(home.address().state()).isEqualTo("PA");
        assertThat(home.address().zip()).isEqualTo("15213");
        AddressResponse office = add("ada@test.com", " ", "9 Work Rd", false);
        assertThat(office.isDefault()).isFalse();
        assertThat(office.label()).isNull();
        assertThat(service.list("ada@test.com")).extracting(AddressResponse::id).containsExactly(home.id(), office.id());
    }

    @Test
    void makeDefaultMovesTheDefaultSoThereIsAlwaysExactlyOne() {
        AddressResponse home = add("ada@test.com", "Home", "1 Main St", false);
        AddressResponse office = add("ada@test.com", "Office", "9 Work Rd", true);
        assertThat(office.isDefault()).isTrue();
        assertThat(service.list("ada@test.com")).filteredOn(AddressResponse::isDefault).extracting(AddressResponse::id)
                .containsExactly(office.id());
        service.makeDefault("ada@test.com", home.id());
        assertThat(service.list("ada@test.com")).filteredOn(AddressResponse::isDefault).extracting(AddressResponse::id)
                .containsExactly(home.id());
    }

    @Test
    void theSameAddressTwiceIsRefusedWhateverTheCaseSpacesPhoneOrLabel() {
        add("ada@test.com", "Home", "1 Main St", false);
        ShippingAddress twin = new ShippingAddress("ADA  lovelace", "1 main st", "", "pittsburgh", "PA", "15213", null);
        assertThatThrownBy(() -> service.add("ada@test.com", new AddressRequest("Other", twin, false)))
                .isInstanceOf(ConflictException.class).hasMessage("This address is already in your address book as \"Home\"");
        add("bob@test.com", null, "1 Main St", false);                        // another customer may have it
    }

    @Test
    void anUpdateMayKeepItsOwnAddressButNotBecomeAnotherOne() {
        AddressResponse home = add("ada@test.com", "Home", "1 Main St", false);
        AddressResponse office = add("ada@test.com", null, "9 Work Rd", false);
        AddressResponse renamed = service.update("ada@test.com", home.id(), new AddressRequest("My home", at("1 Main St"), false));
        assertThat(renamed.label()).isEqualTo("My home");
        assertThat(renamed.isDefault()).isTrue();                             // still the default
        assertThatThrownBy(() -> service.update("ada@test.com", office.id(), new AddressRequest(null, at("1 MAIN ST"), false)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already in your address book");
        AddressResponse moved = service.update("ada@test.com", office.id(), new AddressRequest(null, at("10 Work Rd"), true));
        assertThat(moved.address().line1()).isEqualTo("10 Work Rd");
        assertThat(moved.isDefault()).isTrue();
        assertThat(service.list("ada@test.com")).filteredOn(AddressResponse::isDefault).hasSize(1);
    }

    @Test
    void theBookHoldsTenAddresses() {
        for (int i = 1; i <= AddressService.MAX; i++) add("ada@test.com", null, i + " Main St", false);
        assertThatThrownBy(() -> add("ada@test.com", null, "11 Main St", false))
                .isInstanceOf(ConflictException.class).hasMessage("Your address book is full (10 addresses). Delete one first.");
    }

    @Test
    void deletingTheDefaultMakesTheNewestRemainingAddressTheDefault() {
        AddressResponse a = add("ada@test.com", null, "1 Main St", false);
        AddressResponse b = add("ada@test.com", null, "2 Main St", false);
        AddressResponse c = add("ada@test.com", null, "3 Main St", false);
        service.delete("ada@test.com", a.id());
        assertThat(service.list("ada@test.com")).extracting(AddressResponse::id).containsExactly(c.id(), b.id());
        assertThat(service.list("ada@test.com").get(0).isDefault()).isTrue();
        service.delete("ada@test.com", b.id());                              // not the default: nothing moves
        assertThat(service.list("ada@test.com")).extracting(AddressResponse::isDefault).containsExactly(true);
        service.delete("ada@test.com", c.id());
        assertThat(service.list("ada@test.com")).isEmpty();
    }

    @Test
    void somebodyElsesAddressDoesNotExistForYou() {
        AddressResponse bobs = add("bob@test.com", null, "5 Elm St", false);
        assertThatThrownBy(() -> service.update("ada@test.com", bobs.id(), new AddressRequest(null, at("6 Elm St"), false)))
                .isInstanceOf(NotFoundException.class).hasMessage("Address not found: " + bobs.id());
        assertThatThrownBy(() -> service.makeDefault("ada@test.com", bobs.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete("ada@test.com", bobs.id())).isInstanceOf(NotFoundException.class);
        verify(repo, never()).delete(any());
        assertThat(db).hasSize(1);
    }

    @Test
    void anUnknownUserIsNotFound() {
        when(users.findByEmail("ghost@test.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list("ghost@test.com")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theKeyIgnoresCaseSpacesPhoneAndAnEmptySecondLine() {
        ShippingAddress a = new ShippingAddress("Ada", "1  Main St", null, "Erie", "PA", "16501", "1");
        ShippingAddress b = new ShippingAddress("ADA", "1 main st", "", "ERIE", "PA", "16501", "2");
        ShippingAddress c = new ShippingAddress("Ada", "1 Main St", "Apt 2", "Erie", "PA", "16501", "1");
        assertThat(AddressService.key(a)).isEqualTo(AddressService.key(b));
        assertThat(AddressService.key(a)).isNotEqualTo(AddressService.key(c));
    }
}
