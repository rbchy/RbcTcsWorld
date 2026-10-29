package com.rbctcsworld.ecommerce.seo;

import com.rbctcsworld.ecommerce.common.SharedEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * robots.txt and sitemap.xml for search engines (ADR-0019).
 * <ul>
 *   <li>The addresses in the sitemap are real paths ({@code /products/12/travel-mug}), which the storefront opens like
 *       its own {@code #/} routes; search engines ignore everything after a {@code #}.</li>
 *   <li>One address per item: the sizes and colours of a variant group are one page (its first variant).</li>
 *   <li>The public demo and a shop without an https address are never indexed: robots.txt disallows everything.</li>
 * </ul>
 */
@Service
public class SeoService {

    /** The public pages besides products, as the storefront names them. */
    static final List<String> PAGES = List.of("/", "/contact", "/help", "/privacy", "/terms", "/returns-policy", "/shipping-policy");
    static final int MAX_URLS = 50_000;                          // the sitemap protocol's limit per file

    record Item(long id, String name, String variantGroup, String imageUrl) {
    }

    private final JdbcTemplate jdbc;
    private final String base;
    private final boolean indexable;

    @Autowired
    public SeoService(JdbcTemplate jdbc, Environment env, @Value("${app.public-url:http://localhost:5173}") String publicUrl) {
        this(jdbc, publicUrl, !SharedEnvironment.isDemo(env) && publicUrl.trim().startsWith("https://"));
    }

    SeoService(JdbcTemplate jdbc, String publicUrl, boolean indexable) {
        this.jdbc = jdbc;
        this.base = publicUrl.trim().replaceAll("/+$", "");
        this.indexable = indexable;
    }

    public String robots() {
        if (!indexable) {
            return "# Not a public store (demo or development): nothing here should be in a search engine.\n"
                    + "User-agent: *\nDisallow: /\n";
        }
        return "User-agent: *\n"
                + "Disallow: /api/admin/\n"
                + "Disallow: /api/auth/\n"
                + "Allow: /\n\n"
                + "Sitemap: " + base + "/sitemap.xml\n";
    }

    public String sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" "
                + "xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\">\n");
        for (String page : PAGES) xml.append("  <url><loc>").append(escape(base + page)).append("</loc></url>\n");
        for (Item item : items()) {
            xml.append("  <url><loc>").append(escape(base + productPath(item.id(), item.name()))).append("</loc>");
            String image = imageAddress(item.imageUrl());
            if (image != null) xml.append("<image:image><image:loc>").append(escape(image)).append("</image:loc></image:image>");
            xml.append("</url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    /** Products on sale, one per variant group (the first one, as on the storefront's cards). */
    List<Item> items() {
        List<Item> all = jdbc.query("SELECT id, name, variant_group, image_url FROM products WHERE active = true ORDER BY id",
                (rs, n) -> new Item(rs.getLong("id"), rs.getString("name"), rs.getString("variant_group"), rs.getString("image_url")));
        Set<String> groups = new HashSet<>();
        int limit = MAX_URLS - PAGES.size();
        return all.stream().filter(i -> i.variantGroup() == null || groups.add(i.variantGroup())).limit(limit).toList();
    }

    /** /products/12/travel-mug - the same form the storefront writes into its canonical link. */
    static String productPath(long id, String name) {
        String slug = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return "/products/" + id + (slug.isEmpty() ? "" : "/" + (slug.length() > 80 ? slug.substring(0, 80).replaceAll("-$", "") : slug));
    }

    /** The shop's own pictures get their full address; outside links stay as they are; none: no image entry. */
    String imageAddress(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        return imageUrl.startsWith("/") ? base + imageUrl : imageUrl;
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
