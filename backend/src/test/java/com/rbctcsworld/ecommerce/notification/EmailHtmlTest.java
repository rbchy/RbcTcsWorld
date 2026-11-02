package com.rbctcsworld.ecommerce.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** REQ-NTF-08: the HTML twin of every e-mail (ADR-0025). */
class EmailHtmlTest {

    private static String html(String text) {
        return EmailHtml.render("RbcTcsWorld Shop", "Order ORD-1 received - $29.84", text);
    }

    @Test
    void theStoreNameSubjectAndEveryParagraphAreThere() {
        String h = html("Thank you for your order.\nWe pack it today.\n\nQuestions? Reply to this e-mail.");
        assertThat(h).startsWith("<!doctype html>").endsWith("</html>")
                .contains(">RbcTcsWorld Shop</td>")
                .contains("<h1 style=\"font-size:18px;margin:0 0 16px;\">Order ORD-1 received - $29.84</h1>")
                .contains(">Thank you for your order.<br>We pack it today.</p>")
                .contains(">Questions? Reply to this e-mail.</p>");
    }

    @Test
    void nothingTheCustomerTypedCanBecomeMarkup() {
        String h = EmailHtml.render("Shop <b>", "Re: <script>", "Your message: <img src=x onerror=alert(1)> & \"quotes\" 'too'");
        assertThat(h).doesNotContain("<script>", "<img", "<b>")
                .contains("Shop &lt;b&gt;", "Re: &lt;script&gt;",
                        "&lt;img src=x onerror=alert(1)&gt; &amp; &quot;quotes&quot; &#39;too&#39;");
    }

    @Test
    void onlyHttpsLinksBecomeLinksWithoutTheirFullStop() {
        assertThat(EmailHtml.links("Reset here: https://shop.example/#/reset?token=a1b2. Thanks"))
                .isEqualTo("Reset here: <a href=\"https://shop.example/#/reset?token=a1b2\" style=\"color:#5b2fd6;\">"
                        + "https://shop.example/#/reset?token=a1b2</a>. Thanks");
        assertThat(EmailHtml.links("http://plain.example and javascript:alert(1)"))
                .isEqualTo("http://plain.example and javascript:alert(1)");
        assertThat(EmailHtml.links("https://x.example/?q=\"><script>")).doesNotContain("<script>").doesNotContain("\"><");
    }

    @Test
    void thePriceBreakdownKeepsItsColumns() {
        String h = html("Items:\n\n2 x Wireless Mouse  $25.00\nSubtotal $25.00\nShipping $5.99\nTotal $30.99");
        assertThat(h).contains("<pre style=").contains("2 x Wireless Mouse  $25.00\nSubtotal $25.00");
        assertThat(h).contains("<p style=\"margin:0 0 16px;\">Items:</p>");                // one line: a paragraph
    }

    @Test
    void blankLinesAndWindowsLineEndingsMakeNoEmptyParagraphs() {
        assertThat(EmailHtml.paragraphs("a\r\n\r\n\r\n  \nb\r\nc\n\n")).containsExactly("a", "b\nc");
    }

    @Test
    void noImagesSoOpeningTheMailFetchesNothing() {
        assertThat(html("Hello")).doesNotContain("<img").doesNotContain("src=");
    }
}
