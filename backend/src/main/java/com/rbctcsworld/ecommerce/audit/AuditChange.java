package com.rbctcsworld.ecommerce.audit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One changed field of one record: what the audit trail stores per row. */
public record AuditChange(String entityType, long entityId, String action, String field, String oldValue,
                          String newValue) {

    public static final String PRODUCT = "PRODUCT";
    public static final String ORDER = "ORDER";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String COUPON = "COUPON";
    public static final int MAX_VALUE = 500;

    /**
     * The fields whose value differs between before and after, in the order of {@code after}.
     * {@code before} = null means the record was created (every field with a value is listed).
     * Numbers are compared by value: 12.5 and 12.50 are the same price.
     */
    public static List<AuditChange> diff(String entityType, long entityId, String action,
                                         Map<String, Object> before, Map<String, Object> after) {
        List<AuditChange> changes = new ArrayList<>();
        for (Map.Entry<String, Object> e : after.entrySet()) {
            Object old = before == null ? null : before.get(e.getKey());
            if (!same(old, e.getValue())) {
                changes.add(new AuditChange(entityType, entityId, action, e.getKey(), text(old), text(e.getValue())));
            }
        }
        return changes;
    }

    /** Convenience for building the before/after maps in field order. */
    public static Map<String, Object> fields(Object... namesAndValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            m.put((String) namesAndValues[i], namesAndValues[i + 1]);
        }
        return m;
    }

    static boolean same(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }

    /** As stored: plain text, BigDecimal without exponent, at most MAX_VALUE characters, null stays null. */
    static String text(Object value) {
        if (value == null) return null;
        String s = value instanceof BigDecimal d ? d.toPlainString() : String.valueOf(value);
        return s.length() > MAX_VALUE ? s.substring(0, MAX_VALUE) : s;
    }
}
