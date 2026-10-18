package com.rbctcsworld.ecommerce.address;

import com.rbctcsworld.ecommerce.address.AddressDtos.AddressRequest;
import com.rbctcsworld.ecommerce.address.AddressDtos.AddressResponse;
import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.order.ShippingAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * The customer's address book. Rules:
 *  - at most {@value #MAX} addresses (409 when full);
 *  - the same address twice is refused (409; compared without case and extra spaces, label ignored);
 *  - the first address is the default; makeDefault moves the default; deleting the default makes the newest
 *    remaining address the default;
 *  - somebody else's address does not exist for you (404), so ids cannot be probed.
 */
@Service
@Transactional
public class AddressService {

    public static final int MAX = 10;

    private final CustomerAddressRepository repo;
    private final UserRepository users;

    public AddressService(CustomerAddressRepository repo, UserRepository users) {
        this.repo = repo;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(String email) {
        return mine(user(email).getId()).stream().map(AddressResponse::from).toList();
    }

    public AddressResponse add(String email, AddressRequest r) {
        Long userId = user(email).getId();
        List<CustomerAddress> all = mine(userId);
        if (all.size() >= MAX) throw new ConflictException("Your address book is full (" + MAX + " addresses). Delete one first.");
        ShippingAddress a = r.address().normalized();
        refuseTwin(all, a, null);
        CustomerAddress saved = new CustomerAddress(userId);
        saved.change(label(r.label()), a);
        boolean first = all.isEmpty();
        if (first || r.makeDefault()) clearDefault(all);
        saved.setDefault(first || r.makeDefault());
        return AddressResponse.from(repo.save(saved));
    }

    public AddressResponse update(String email, long id, AddressRequest r) {
        Long userId = user(email).getId();
        CustomerAddress a = owned(id, userId);
        ShippingAddress normalized = r.address().normalized();
        List<CustomerAddress> all = mine(userId);
        refuseTwin(all, normalized, id);
        a.change(label(r.label()), normalized);
        if (r.makeDefault() && !a.isDefault()) {
            clearDefault(all);
            a.setDefault(true);
        }
        return AddressResponse.from(repo.save(a));
    }

    public AddressResponse makeDefault(String email, long id) {
        Long userId = user(email).getId();
        CustomerAddress a = owned(id, userId);
        clearDefault(mine(userId));
        a.setDefault(true);
        return AddressResponse.from(repo.save(a));
    }

    public void delete(String email, long id) {
        Long userId = user(email).getId();
        CustomerAddress a = owned(id, userId);
        boolean wasDefault = a.isDefault();
        repo.delete(a);
        repo.flush();
        if (wasDefault) {
            mine(userId).stream().max(java.util.Comparator.comparing(CustomerAddress::getCreatedAt)
                            .thenComparing(CustomerAddress::getId))
                    .ifPresent(next -> { next.setDefault(true); repo.save(next); });
        }
    }

    private void refuseTwin(List<CustomerAddress> all, ShippingAddress a, Long self) {
        String key = key(a);
        for (CustomerAddress other : all) {
            if (!other.getId().equals(self) && key(other.address()).equals(key)) {
                throw new ConflictException("This address is already in your address book"
                        + (other.getLabel() == null ? "" : " as \"" + other.getLabel() + "\""));
            }
        }
    }

    /** Name, street, city, state and ZIP without case or extra spaces (phone and label do not make it another place). */
    static String key(ShippingAddress a) {
        return String.join("|", a.fullName(), a.line1(), a.line2() == null ? "" : a.line2(), a.city(), a.state(), a.zip())
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String label(String label) {
        return label == null || label.isBlank() ? null : label.trim().replaceAll("\\s+", " ");
    }

    private void clearDefault(List<CustomerAddress> all) {
        for (CustomerAddress a : all) {
            if (a.isDefault()) {
                a.setDefault(false);
                repo.save(a);
            }
        }
        repo.flush();
    }

    private List<CustomerAddress> mine(Long userId) {
        return repo.findByUserIdOrderByIsDefaultDescCreatedAtDescIdDesc(userId);
    }

    private CustomerAddress owned(long id, Long userId) {
        return repo.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Address not found: " + id));
    }

    private AppUser user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
