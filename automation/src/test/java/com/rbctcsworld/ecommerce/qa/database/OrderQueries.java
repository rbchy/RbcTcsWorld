package com.rbctcsworld.ecommerce.qa.database;

import java.util.List;
import java.util.Map;

/** Named SQL for order/inventory validation - keeps raw SQL out of the test methods. */
public final class OrderQueries {

    private OrderQueries() {
    }

    public static Map<String, Object> order(long orderId) {
        List<Map<String, Object>> rows = DatabaseUtils.query(
                "select id, order_number, status, total_items, subtotal, cancelled_at, ship_name, ship_line1, ship_line2, ship_city,"
                        + " ship_state, ship_zip, ship_phone from orders where id = ?", orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static List<Map<String, Object>> orderItems(long orderId) {
        return DatabaseUtils.query(
                "select product_id, sku, unit_price, quantity, line_total from order_items where order_id = ? order by product_id",
                orderId);
    }

    public static int productStock(long productId) {
        return ((Number) DatabaseUtils.scalar("select stock from products where id = ?", productId)).intValue();
    }

    public static List<Map<String, Object>> stockMovementsForOrder(long orderId) {
        return DatabaseUtils.query(
                "select product_id, change_qty, reason, stock_after from stock_movements where order_id = ? order by id",
                orderId);
    }

    public static int cartLineCount(String email) {
        return ((Number) DatabaseUtils.scalar(
                "select count(*) from cart_items ci join users u on u.id = ci.user_id where u.email = ?", email)).intValue();
    }
}
