package com.rbctcsworld.ecommerce.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The HTML twin of a plain-text e-mail (ADR-0025). The plain text stays the source of truth and is always sent too
 * (multipart/alternative): every mail client shows one of the two, screen readers and filters read the text.
 *
 * The HTML is built from the text, never from anything else, so nothing a customer typed can become markup:
 *  - every character is escaped first (&lt; &gt; &amp; &quot; &#39;);
 *  - blank lines separate paragraphs, single line breaks become &lt;br&gt;;
 *  - https:// links become links (only https, only the characters a URL may hold);
 *  - "Label  $12.34" lines (the price breakdown) keep their spacing in a monospace block.
 * Layout: one 600 px table with inline styles (what e-mail clients understand), the store name in the brand colour
 * as the header, no images (nothing is fetched when the mail is opened, so no tracking), a short footer.
 */
public final class EmailHtml {

    static final String ACCENT = "#5b2fd6";
    static final String INK = "#1d1934";
    static final String MUTED = "#5b5672";
    private static final Pattern LINK = Pattern.compile("https://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+");
    /** A price line such as "2 x Wireless Mouse  $25.00" or "Total $29.84". */
    private static final Pattern MONEY_LINE = Pattern.compile(".*\\s\\-?\\$\\d[\\d,]*\\.\\d{2}\\s*");

    private EmailHtml() {
    }

    public static String render(String storeName, String subject, String text) {
        StringBuilder html = new StringBuilder(text.length() * 2 + 1200);
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(escape(subject)).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:#f5f3fb;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f5f3fb;\">")
                .append("<tr><td align=\"center\" style=\"padding:24px 12px;\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"max-width:600px;width:100%;background:#ffffff;border-radius:8px;")
                .append("font-family:Arial,Helvetica,sans-serif;color:").append(INK).append(";\">")
                .append("<tr><td style=\"background:").append(ACCENT).append(";color:#ffffff;padding:18px 24px;")
                .append("font-size:20px;font-weight:bold;border-radius:8px 8px 0 0;\">").append(escape(storeName))
                .append("</td></tr>")
                .append("<tr><td style=\"padding:24px;font-size:15px;line-height:1.55;\">")
                .append("<h1 style=\"font-size:18px;margin:0 0 16px;\">").append(escape(subject)).append("</h1>");
        for (String paragraph : paragraphs(text)) {
            html.append(block(paragraph));
        }
        html.append("</td></tr>")
                .append("<tr><td style=\"padding:16px 24px;border-top:1px solid #e3dfef;font-size:12px;color:")
                .append(MUTED).append(";\">").append(escape(storeName))
                .append(" &middot; This e-mail is about your account or an order. Reply to reach us.</td></tr>")
                .append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    /** Paragraphs: text between blank lines, line endings unified, empty ones dropped. */
    static List<String> paragraphs(String text) {
        List<String> out = new ArrayList<>();
        for (String p : text.replace("\r\n", "\n").replace('\r', '\n').split("\n\\s*\n")) {
            if (!p.isBlank()) out.add(p.strip());
        }
        return out;
    }

    private static String block(String paragraph) {
        String[] lines = paragraph.split("\n");
        long money = java.util.Arrays.stream(lines).filter(l -> MONEY_LINE.matcher(l).matches()).count();
        if (lines.length > 1 && money * 2 > lines.length) {           // mostly price lines: keep their columns
            return "<pre style=\"font-family:Menlo,Consolas,monospace;font-size:13px;background:#f5f3fb;"
                    + "padding:12px;border-radius:6px;white-space:pre-wrap;margin:0 0 16px;\">"
                    + escape(paragraph) + "</pre>";
        }
        List<String> rendered = new ArrayList<>();
        for (String line : lines) rendered.add(links(line));
        return "<p style=\"margin:0 0 16px;\">" + String.join("<br>", rendered) + "</p>";
    }

    /** Escapes the line, then turns https links into anchors (the link text is the escaped URL itself). */
    static String links(String line) {
        Matcher m = LINK.matcher(line);
        StringBuilder out = new StringBuilder();
        int at = 0;
        while (m.find()) {
            String url = trimPunctuation(m.group());
            out.append(escape(line.substring(at, m.start())));
            out.append("<a href=\"").append(escape(url)).append("\" style=\"color:").append(ACCENT).append(";\">")
                    .append(escape(url)).append("</a>");
            at = m.start() + url.length();
        }
        out.append(escape(line.substring(at)));
        return out.toString();
    }

    /** "see https://x.com/a." -> the full stop is not part of the link. */
    private static String trimPunctuation(String url) {
        String u = url;
        while (!u.isEmpty() && ".,;:!?)'".indexOf(u.charAt(u.length() - 1)) >= 0) u = u.substring(0, u.length() - 1);
        return u;
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
