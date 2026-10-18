package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.order.OrderDtos.OrderLine;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderResponse;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The invoice of an order as a PDF, written by hand (no library): the PDF is text, a coloured band and thin rules,
 * which needs only the two standard fonts every viewer has (Helvetica and Helvetica-Bold).
 *
 * Layout: US Letter, 50 pt margins. Page 1 has the header band, order facts and the address; the item table
 * continues on more pages when there are many lines; the totals follow the last line.
 * Text is Latin-1; anything else is printed as "?" so a customer name can never break the file.
 */
public final class InvoicePdf {

    static final int PAGE_W = 612;
    static final int PAGE_H = 792;
    static final int LINES_FIRST = 22;
    static final int LINES_NEXT = 40;
    static final int NAME_MAX = 38;

    private InvoicePdf() {
    }

    /** Money as printed on the invoice: $1234.50 */
    static String money(BigDecimal v) {
        return "$" + (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Text safe inside a PDF string: Latin-1 only, with \ ( ) escaped. */
    static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : (s == null ? "" : s).toCharArray()) {
            if (c == '\\' || c == '(' || c == ')') b.append('\\').append(c);
            else if (c < 32 || c > 255) b.append('?');
            else b.append(c);
        }
        return b.toString();
    }

    /** Helvetica widths in 1/1000 em for the characters of money and numbers; the average for the rest. */
    static double width(String s, double size) {
        double w = 0;
        for (char c : s.toCharArray()) w += (Character.isDigit(c) || c == '$') ? 556 : (c == '.' || c == ',' || c == ' ' ? 278 : c == '-' ? 333 : 540);
        return w * size / 1000.0;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }

    /** Builds one page's content stream. */
    private static final class Page {
        final StringBuilder out = new StringBuilder();

        void text(double x, double y, String font, double size, String s) {
            out.append("BT /").append(font).append(' ').append(size).append(" Tf ").append(x).append(' ').append(y)
                    .append(" Td (").append(escape(s)).append(") Tj ET\n");
        }

        void right(double xRight, double y, String font, double size, String s) {
            text(xRight - width(s, size), y, font, size, s);
        }

        void rect(double x, double y, double w, double h, String rgb) {
            out.append(rgb).append(" rg ").append(x).append(' ').append(y).append(' ').append(w).append(' ').append(h).append(" re f 0 g\n");
        }

        void rule(double y) {
            rect(50, y, 512, 0.6, "0.75 0.78 0.80");
        }
    }

    public static byte[] render(OrderResponse o) {
        List<Page> pages = new ArrayList<>();
        Page p = new Page();
        pages.add(p);

        p.rect(0, 722, PAGE_W, 70, "0.71 0.33 0.04");
        p.text(50, 752, "F2", 24, "RbcTcsWorld");
        p.text(50, 733, "F1", 10, "Everyday goods, delivered to your door");
        p.right(562, 752, "F2", 22, "INVOICE");

        p.text(50, 690, "F2", 11, "Order " + o.orderNumber());
        p.text(50, 674, "F1", 10, "Date: " + (o.createdAt() == null ? "" : o.createdAt().toLocalDate()));
        p.text(50, 660, "F1", 10, "Status: " + o.status() + (o.paidAt() == null ? "  (payment due)" : "  (paid " + o.paidAt().toLocalDate() + ")"));
        ShippingAddress a = o.shippingAddress();
        if (a != null) {
            p.text(330, 690, "F2", 11, "Ship to");
            p.text(330, 674, "F1", 10, a.fullName());
            p.text(330, 660, "F1", 10, a.line1() + (a.line2() == null ? "" : ", " + a.line2()));
            p.text(330, 646, "F1", 10, a.city() + ", " + a.state() + " " + a.zip());
        }

        double y = 610;
        header(p, y);
        y -= 20;
        int onPage = 0;
        int limit = LINES_FIRST;
        for (OrderLine l : o.items()) {
            if (onPage == limit) {
                p = new Page();
                pages.add(p);
                y = 740;
                header(p, y);
                y -= 20;
                onPage = 0;
                limit = LINES_NEXT;
            }
            p.text(50, y, "F1", 9, cut(l.sku() == null ? "" : l.sku(), 18));
            p.text(140, y, "F1", 9, cut(l.name() == null ? "" : l.name(), NAME_MAX));
            p.right(400, y, "F1", 9, String.valueOf(l.quantity()));
            p.right(480, y, "F1", 9, money(l.unitPrice()));
            p.right(562, y, "F1", 9, money(l.lineTotal()));
            y -= 16;
            onPage++;
        }

        if (y < 180) {
            p = new Page();
            pages.add(p);
            y = 740;
        }
        p.rule(y + 8);
        y -= 10;
        y = total(p, y, "Subtotal", money(o.subtotal()), false);
        if (o.discount() != null && o.discount().signum() > 0) {
            y = total(p, y, "Discount" + (o.couponCode() == null ? "" : " (" + o.couponCode() + ")"), "-" + money(o.discount()), false);
        }
        y = total(p, y, "Shipping", money(o.shippingFee()), false);
        y = total(p, y, "Tax", money(o.tax()), false);
        total(p, y - 2, "Total", money(o.total()), true);

        int last = pages.size();
        for (int i = 0; i < last; i++) {
            pages.get(i).text(50, 30, "F1", 8, "RbcTcsWorld E-Commerce  |  " + o.orderNumber() + "  |  page " + (i + 1) + " of " + last);
        }
        return assemble(pages);
    }

    private static void header(Page p, double y) {
        p.rect(50, y - 6, 512, 18, "0.91 0.94 0.93");
        p.text(54, y, "F2", 9, "SKU");
        p.text(140, y, "F2", 9, "Item");
        p.right(400, y, "F2", 9, "Qty");
        p.right(480, y, "F2", 9, "Unit price");
        p.right(562, y, "F2", 9, "Total");
    }

    private static double total(Page p, double y, String label, String value, boolean bold) {
        String font = bold ? "F2" : "F1";
        double size = bold ? 12 : 10;
        p.text(380 - (bold ? 0 : 0), y, font, size, label);
        p.right(562, y, font, size, value);
        return y - (bold ? 18 : 15);
    }

    /** Objects: 1 catalog, 2 pages, 3 Helvetica, 4 Helvetica-Bold, then (content, page) per page. */
    private static byte[] assemble(List<Page> pages) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        int n = pages.size();
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) kids.append(6 + 2 * i).append(" 0 R ");

        obj(out, offsets, "<< /Type /Catalog /Pages 2 0 R >>");
        obj(out, offsets, "<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + n + " >>");
        obj(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        obj(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        for (int i = 0; i < n; i++) {
            String content = pages.get(i).out.toString();
            int len = content.getBytes(StandardCharsets.ISO_8859_1).length;
            obj(out, offsets, "<< /Length " + len + " >>\nstream\n" + content + "endstream");
            obj(out, offsets, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_W + " " + PAGE_H + "] /Contents " + (5 + 2 * i)
                    + " 0 R /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> >>");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
        for (int off : offsets) write(out, String.format("%010d 00000 n \n", off));
        write(out, "trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void obj(ByteArrayOutputStream out, List<Integer> offsets, String body) {
        offsets.add(out.size());
        write(out, offsets.size() + " 0 obj\n" + body + "\nendobj\n");
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
