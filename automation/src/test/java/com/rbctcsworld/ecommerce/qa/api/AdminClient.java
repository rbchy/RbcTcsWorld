package com.rbctcsworld.ecommerce.qa.api;

import io.restassured.response.Response;

import java.util.Map;

public class AdminClient extends ApiClient {

    public Response notifications(String token, long orderId) {
        return as(token).queryParam("orderId", orderId).get("/api/admin/notifications");
    }

    public Response retryNotification(String token, long notificationId) {
        return as(token).post("/api/admin/notifications/{id}/retry", notificationId);
    }

    public Response allOrders(String token) {
        return as(token).get("/api/admin/orders");
    }

    public Response createCoupon(String token, Map<String, Object> body) {
        return as(token).body(body).post("/api/admin/coupons");
    }

    public Response ship(String token, long orderId, String carrier) {
        return as(token).body(Map.of("carrier", carrier)).post("/api/admin/orders/{id}/ship", orderId);
    }

    public Response deliver(String token, long orderId) {
        return as(token).post("/api/admin/orders/{id}/deliver", orderId);
    }

    public Response returns(String token, String status) {
        return status == null ? as(token).get("/api/admin/returns")
                : as(token).queryParam("status", status).get("/api/admin/returns");
    }

    public Response approveReturn(String token, long returnId) {
        return as(token).post("/api/admin/returns/{id}/approve", returnId);
    }

    public Response rejectReturn(String token, long returnId, String note) {
        return as(token).body(Map.of("note", note)).post("/api/admin/returns/{id}/reject", returnId);
    }

    public Response stockMovements(String token, long productId) {
        return as(token).get("/api/admin/products/{id}/stock-movements", productId);
    }

    /** Audit trail, newest first. Any filter may be null. */
    public Response audit(String token, String entityType, Long entityId, String actor) {
        var r = as(token);
        if (entityType != null) r = r.queryParam("entityType", entityType);
        if (entityId != null) r = r.queryParam("entityId", entityId);
        if (actor != null) r = r.queryParam("actor", actor);
        return r.get("/api/admin/audit");
    }

    public Response auditVerify(String token) {
        return as(token).get("/api/admin/audit/verify");
    }

    public Response auditExport(String token, String entityType, Long entityId) {
        return as(token).queryParam("entityType", entityType).queryParam("entityId", entityId)
                .get("/api/admin/audit/export");
    }
}
