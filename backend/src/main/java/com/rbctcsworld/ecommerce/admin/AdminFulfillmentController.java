package com.rbctcsworld.ecommerce.admin;

import com.rbctcsworld.ecommerce.fulfillment.FulfillmentDtos.LabelResponse;
import com.rbctcsworld.ecommerce.fulfillment.FulfillmentDtos.ShipRequest;
import com.rbctcsworld.ecommerce.fulfillment.FulfillmentService;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderResponse;
import com.rbctcsworld.ecommerce.returns.ReturnDtos.ResolveReturnRequest;
import com.rbctcsworld.ecommerce.returns.ReturnRequest;
import com.rbctcsworld.ecommerce.returns.ReturnService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Warehouse + customer-service back office (ADMIN only via SecurityConfig /api/admin/**).
 * POST /api/admin/orders/{id}/ship {carrier}   200 | 400 | 404 | 409 not PAID
 *                                              (400 also: no rate / address refused; 503 shipping provider down)
 * POST /api/admin/orders/{id}/deliver          200 | 404 | 409 not SHIPPED
 * GET  /api/admin/orders/{id}/label            200 the label to print | 404 no label
 * GET  /api/admin/returns?status=REQUESTED     200
 * POST /api/admin/returns/{id}/approve {note?} 200 | 404 | 409 already resolved
 * POST /api/admin/returns/{id}/reject  {note?} 200 | 404 | 409 already resolved
 */
@RestController
@RequestMapping("/api/admin")
public class AdminFulfillmentController {

    private final FulfillmentService fulfillment;
    private final ReturnService returns;

    public AdminFulfillmentController(FulfillmentService fulfillment, ReturnService returns) {
        this.fulfillment = fulfillment;
        this.returns = returns;
    }

    @PostMapping("/orders/{id}/ship")
    public OrderResponse ship(@PathVariable Long id, @Valid @RequestBody ShipRequest r) {
        return fulfillment.ship(id, r.carrier());
    }

    @PostMapping("/orders/{id}/deliver")
    public OrderResponse deliver(@PathVariable Long id) {
        return fulfillment.deliver(id);
    }

    @GetMapping("/orders/{id}/label")
    public LabelResponse label(@PathVariable Long id) {
        return fulfillment.label(id);
    }

    @GetMapping("/returns")
    public List<ReturnRequest> returns(@RequestParam(required = false) String status) {
        return returns.list(status);
    }

    @PostMapping("/returns/{id}/approve")
    public ReturnRequest approve(@PathVariable Long id, @Valid @RequestBody(required = false) ResolveReturnRequest r) {
        return returns.approve(id, r == null ? null : r.note());
    }

    @PostMapping("/returns/{id}/reject")
    public ReturnRequest reject(@PathVariable Long id, @Valid @RequestBody(required = false) ResolveReturnRequest r) {
        return returns.reject(id, r == null ? null : r.note());
    }
}
