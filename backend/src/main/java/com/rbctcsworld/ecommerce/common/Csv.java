package com.rbctcsworld.ecommerce.common;

import java.util.List;

/** CSV that opens safely in a spreadsheet: every text cell quoted, formulas defused (OWASP CSV injection). */
public final class Csv {

    private Csv() {
    }

    /**
     * One text cell: quoted, inner quotes doubled. A value a spreadsheet would run as a formula
     * (=, +, -, @, tab, carriage return at the start) gets a leading apostrophe. null = empty cell.
     */
    public static String cell(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /** Numbers as they are (so the spreadsheet can add them up), everything else as a text cell. */
    public static String value(Object v) {
        if (v == null) return "";
        if (v instanceof Number n) return n instanceof java.math.BigDecimal d ? d.toPlainString() : n.toString();
        return cell(v.toString());
    }

    /** One line, CRLF-terminated (RFC 4180). */
    public static String line(List<?> values) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) s.append(',');
            s.append(value(values.get(i)));
        }
        return s.append("\r\n").toString();
    }
}
