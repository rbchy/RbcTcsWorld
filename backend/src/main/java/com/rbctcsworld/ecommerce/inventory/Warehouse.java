package com.rbctcsworld.ecommerce.inventory;

/** A place that holds stock. Lower priority number = preferred when nothing else decides. */
public record Warehouse(long id, String code, String name, String city, String state, int priority, boolean active) {
}
