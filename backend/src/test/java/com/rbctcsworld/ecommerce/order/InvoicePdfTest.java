package com.rbctcsworld.ecommerce.order;

import com.rbctcsworld.ecommerce.order.OrderDtos.OrderLine;
import com.rbctcsworld.ecommerce.order.OrderDtos.OrderResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The invoice PDF, checked as a file format: REQ-ORD-11. */
class InvoicePdfTest {

    private static final ShippingAddress ADDRESS = new ShippingAddress("Ada Lovelace", "12 Main St", "Apt 4", "Pittsburgh", "PA", "15213", null);

    private static OrderLine line(int i, String name, int qty, String price) {
        BigDecimal unit = new BigDecimal(price);
        return new OrderLine((long) i, "SKU-" + i, name, unit, qty, unit.multiply(BigDecimal.valueOf(qty)));
    }

    private static OrderResponse order(List<OrderLine> lines, String discount, String coupon, LocalDateTime paidAt) {
        return new OrderResponse(7L, "ORD-20261018-ABC123", 3L, paidAt == null ? "PLACED" : "PAID", lines, 3,
                new BigDecimal("59.97"), coupon, new BigDecimal(discount), new BigDecimal("5.99"), new BigDecimal("3.24"),
                new BigDecimal("69.20"), null, null, LocalDateTime.of(2026, 10, 18, 9, 30), paidAt, null, null, null, ADDRESS);
    }

    private static String text(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    @Test
    void theFileIsAWellFormedPdfWithCorrectOffsets() {
        String pdf = text(InvoicePdf.render(order(List.of(line(1, "Wireless Mouse", 3, "19.99")), "0", null, null)));
        assertThat(pdf).startsWith("%PDF-1.4\n").endsWith("%%EOF\n");
        Matcher start = Pattern.compile("startxref\\n(\\d+)\\n").matcher(pdf);
        assertThat(start.find()).isTrue();
        assertThat(pdf.substring(Integer.parseInt(start.group(1)))).startsWith("xref\n");
        // every cross-reference entry points exactly at "<n> 0 obj"
        Matcher entry = Pattern.compile("(\\d{10}) 00000 n \\n").matcher(pdf);
        int n = 0;
        while (entry.find()) {
            n++;
            assertThat(pdf.substring(Integer.parseInt(entry.group(1)))).startsWith(n + " 0 obj\n");
        }
        assertThat(n).isEqualTo(6);                                           // catalog, pages, 2 fonts, content, page
        assertThat(pdf).contains("/Count 1").contains("/BaseFont /Helvetica-Bold");
    }

    @Test
    void everyStreamLengthIsTheRealLength() {
        String pdf = text(InvoicePdf.render(order(List.of(line(1, "Mug (blue)", 1, "9.99")), "0", null, null)));
        Matcher m = Pattern.compile("/Length (\\d+) >>\\nstream\\n(.*?)endstream", Pattern.DOTALL).matcher(pdf);
        int streams = 0;
        while (m.find()) {
            streams++;
            assertThat(m.group(2).length()).isEqualTo(Integer.parseInt(m.group(1)));
        }
        assertThat(streams).isEqualTo(1);
    }

    @Test
    void theInvoiceShowsTheOrderTheAddressTheLinesAndTheTotals() {
        String pdf = text(InvoicePdf.render(order(List.of(line(1, "Wireless Mouse", 3, "19.99")), "6.00", "WELCOME10", LocalDateTime.of(2026, 10, 18, 9, 45))));
        assertThat(pdf).contains("(INVOICE)", "(Order ORD-20261018-ABC123)", "(Date: 2026-10-18)", "(Status: PAID  \\(paid 2026-10-18\\))",
                "(Ship to)", "(Ada Lovelace)", "(12 Main St, Apt 4)", "(Pittsburgh, PA 15213)",
                "(SKU-1)", "(Wireless Mouse)", "($19.99)", "($59.97)",
                "(Subtotal)", "(Discount \\(WELCOME10\\))", "(-$6.00)", "(Shipping)", "($5.99)", "(Tax)", "($3.24)", "(Total)", "($69.20)",
                "(RbcTcsWorld E-Commerce  |  ORD-20261018-ABC123  |  page 1 of 1)");
    }

    @Test
    void anUnpaidOrderIsMarkedPaymentDueAndHasNoDiscountLine() {
        String pdf = text(InvoicePdf.render(order(List.of(line(1, "Lamp", 1, "30.00")), "0", null, null)));
        assertThat(pdf).contains("(Status: PLACED  \\(payment due\\))").doesNotContain("(Discount");
    }

    @Test
    void brackets_backslashes_andNonLatinTextCannotBreakTheFile() {
        String pdf = text(InvoicePdf.render(order(List.of(line(1, "Tee (M) \\ 测试", 1, "10.00")), "0", null, null)));
        assertThat(pdf).contains("(Tee \\(M\\) \\\\ ??)");
        assertThat(InvoicePdf.escape(null)).isEmpty();
        assertThat(InvoicePdf.escape("a\nb")).isEqualTo("a?b");
    }

    @Test
    void aLongItemNameIsCutWithDots() {
        String name = "A very long product name that would run over the price columns of the table";
        String pdf = text(InvoicePdf.render(order(List.of(line(1, name, 1, "10.00")), "0", null, null)));
        assertThat(pdf).contains("(" + name.substring(0, InvoicePdf.NAME_MAX - 1) + "...)").doesNotContain(name);
    }

    @Test
    void manyLinesContinueOnMorePages() {
        List<OrderLine> lines = new ArrayList<>();
        for (int i = 1; i <= 70; i++) lines.add(line(i, "Item " + i, 1, "1.00"));
        String pdf = text(InvoicePdf.render(order(lines, "0", null, null)));
        // 22 lines on page 1, 40 on the next, 8 on the third; the totals fit below them
        assertThat(pdf).contains("/Count 3", "page 1 of 3", "page 2 of 3", "page 3 of 3", "(Item 70)", "(Total)");
        assertThat(text(InvoicePdf.render(order(List.of(), "0", null, null)))).contains("/Count 1");
    }

    @Test
    void totalsThatDoNotFitBelowTheLastLineMoveToANewPage() {
        List<OrderLine> fits = new ArrayList<>();
        for (int i = 1; i <= 22 + 33; i++) fits.add(line(i, "Item " + i, 1, "1.00"));            // page 2 ends at y=192: the totals fit
        assertThat(text(InvoicePdf.render(order(fits, "0", null, null)))).contains("/Count 2");
        List<OrderLine> overflow = new ArrayList<>();
        for (int i = 1; i <= 22 + 34; i++) overflow.add(line(i, "Item " + i, 1, "1.00"));        // y=176: the totals get page 3
        String pdf = text(InvoicePdf.render(order(overflow, "0", null, null)));
        assertThat(pdf).contains("/Count 3", "page 3 of 3");
    }

    @Test
    void moneyIsRoundedToCentsAndWidthsAlignNumbers() {
        assertThat(InvoicePdf.money(new BigDecimal("5"))).isEqualTo("$5.00");
        assertThat(InvoicePdf.money(new BigDecimal("2.005"))).isEqualTo("$2.01");
        assertThat(InvoicePdf.money(null)).isEqualTo("$0.00");
        assertThat(InvoicePdf.width("12", 10)).isEqualTo(11.12);
        assertThat(InvoicePdf.width("$1.50", 10)).isGreaterThan(InvoicePdf.width("1", 10));
        assertThat(InvoicePdf.width("-,", 10)).isEqualTo(6.11);
    }
}
