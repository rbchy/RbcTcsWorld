package com.rbctcsworld.ecommerce.address;

import com.rbctcsworld.ecommerce.order.ShippingAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AddressDtos {

    private AddressDtos() {
    }

    /**
     * label: optional name such as "Home" or "Office" (letters, digits, spaces, . ' -; up to 30).
     * address: the same rules as an order's delivery address (US only). makeDefault: use it first at checkout.
     */
    public record AddressRequest(
            @Size(max = 30) @Pattern(regexp = "^[\\p{L}\\p{N} .'-]*$", message = "may only contain letters, digits, spaces and . ' -")
            String label,
            @NotNull @Valid ShippingAddress address,
            boolean makeDefault) {
    }

    public record AddressResponse(long id, String label, ShippingAddress address, boolean isDefault) {

        static AddressResponse from(CustomerAddress a) {
            return new AddressResponse(a.getId(), a.getLabel(), a.address(), a.isDefault());
        }
    }
}
