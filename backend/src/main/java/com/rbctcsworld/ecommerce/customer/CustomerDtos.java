package com.rbctcsworld.ecommerce.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    /** One row of the customer list. spent = total of orders that were paid and not cancelled or returned. */
    public record CustomerSummary(long id, String email, boolean active, LocalDateTime createdAt, long orders,
                                  BigDecimal spent, LocalDateTime lastOrderAt) {
    }

    public record CustomerPage(List<CustomerSummary> items, long total, int page, int size) {
    }

    public record CustomerOrder(long id, String orderNumber, String status, BigDecimal total, LocalDateTime createdAt) {
    }

    /** The latest delivery address the customer used (null when they never ordered with one). */
    public record LastAddress(String name, String line1, String line2, String city, String state, String zip,
                              String phone) {
    }

    public record CustomerDetail(CustomerSummary customer, List<CustomerOrder> orders, LastAddress lastAddress) {
    }
}
