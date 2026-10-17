package com.rbctcsworld.ecommerce.customer;

import com.rbctcsworld.ecommerce.customer.CustomerDtos.CustomerDetail;
import com.rbctcsworld.ecommerce.customer.CustomerDtos.CustomerPage;
import com.rbctcsworld.ecommerce.customer.CustomerDtos.CustomerSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store admin only (/api/admin/** in SecurityConfig).
 * GET  /api/admin/customers?q=&status=ACTIVE|DISABLED&sort=newest|email|orders|spent|lastOrder&page=0&size=20
 * GET  /api/admin/customers/{id}            customer + latest orders + last address | 404
 * POST /api/admin/customers/{id}/disable    ends every session | 404
 * POST /api/admin/customers/{id}/enable
 */
@RestController
@RequestMapping("/api/admin/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    public CustomerPage search(@RequestParam(required = false) String q,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String sort,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size) {
        return customers.search(q, status, sort, page, size);
    }

    @GetMapping("/{id}")
    public CustomerDetail detail(@PathVariable long id) {
        return customers.detail(id);
    }

    @PostMapping("/{id}/disable")
    public CustomerSummary disable(@PathVariable long id) {
        return customers.setActive(id, false);
    }

    @PostMapping("/{id}/enable")
    public CustomerSummary enable(@PathVariable long id) {
        return customers.setActive(id, true);
    }
}
