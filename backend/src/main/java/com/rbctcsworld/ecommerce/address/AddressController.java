package com.rbctcsworld.ecommerce.address;

import com.rbctcsworld.ecommerce.address.AddressDtos.AddressRequest;
import com.rbctcsworld.ecommerce.address.AddressDtos.AddressResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The signed-in customer's address book (JWT required).
 * GET    /api/addresses                -> 200 [default first, then newest]
 * POST   /api/addresses                -> 201 | 400 field errors (address.zip ...) | 409 duplicate or book full (10)
 * PUT    /api/addresses/{id}           -> 200 | 400 | 404 not yours | 409 duplicate
 * POST   /api/addresses/{id}/default   -> 200 | 404
 * DELETE /api/addresses/{id}           -> 204 | 404
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService service;

    public AddressController(AddressService service) {
        this.service = service;
    }

    @GetMapping
    public List<AddressResponse> list(Authentication auth) {
        return service.list(auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse add(Authentication auth, @Valid @RequestBody AddressRequest r) {
        return service.add(auth.getName(), r);
    }

    @PutMapping("/{id}")
    public AddressResponse update(Authentication auth, @PathVariable long id, @Valid @RequestBody AddressRequest r) {
        return service.update(auth.getName(), id, r);
    }

    @PostMapping("/{id}/default")
    public AddressResponse makeDefault(Authentication auth, @PathVariable long id) {
        return service.makeDefault(auth.getName(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable long id) {
        service.delete(auth.getName(), id);
    }
}
