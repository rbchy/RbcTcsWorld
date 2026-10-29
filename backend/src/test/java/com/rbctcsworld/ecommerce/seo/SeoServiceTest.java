package com.rbctcsworld.ecommerce.seo;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** REQ-SEO-01..02: robots.txt and sitemap.xml (ADR-0019). */
class SeoServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @SuppressWarnings("unchecked")
    private SeoService shop(SeoService.Item... items) {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(items));
        return new SeoService(jdbc, "https://shop.example/ ", true);
    }

    @Test
    void aLiveShopIsIndexedAndNamesItsSitemap() {
        String robots = shop().robots();
        assertThat(robots).contains("User-agent: *", "Allow: /", "Disallow: /api/admin/", "Disallow: /api/auth/",
                "Sitemap: https://shop.example/sitemap.xml");
        assertThat(robots).doesNotContain("Disallow: /\n");
    }

    @Test
    void theDemoAndAShopWithoutHttpsAreNeverIndexed() {
        String demo = new SeoService(jdbc, new MockEnvironment().withProperty("app.demo.enabled", "true"), "https://demo.example").robots();
        String local = new SeoService(jdbc, new MockEnvironment(), "http://localhost:5173").robots();
        for (String robots : List.of(demo, local)) {
            assertThat(robots).contains("User-agent: *\nDisallow: /\n").doesNotContain("Sitemap:");
        }
        assertThat(new SeoService(jdbc, new MockEnvironment(), " https://shop.example").robots()).contains("Sitemap:");
    }

    @Test
    void theSitemapListsThePublicPagesAndOneAddressPerItemWithItsPicture() {
        String xml = shop(new SeoService.Item(3, "Travel Mug 12 oz", null, "/products/home-mug-002.svg"),
                new SeoService.Item(7, "Classic Tee", "TEE", null),
                new SeoService.Item(8, "Classic Tee", "TEE", "/products/tee-navy.png"),
                new SeoService.Item(9, "Lamp", null, "https://cdn.example.com/lamp.jpg")).sitemap();

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"")
                .contains("<loc>https://shop.example/</loc>", "<loc>https://shop.example/contact</loc>",
                        "<loc>https://shop.example/privacy</loc>", "<loc>https://shop.example/shipping-policy</loc>")
                .contains("<loc>https://shop.example/products/3/travel-mug-12-oz</loc><image:image><image:loc>"
                        + "https://shop.example/products/home-mug-002.svg</image:loc></image:image></url>")
                .contains("<loc>https://shop.example/products/7/classic-tee</loc></url>")
                .doesNotContain("/products/8/")                                    // the tee's other variant: same page
                .contains("<image:loc>https://cdn.example.com/lamp.jpg</image:loc>")
                .endsWith("</urlset>\n");
        assertThat(xml.split("<url>", -1)).hasSize(1 + SeoService.PAGES.size() + 3);
    }

    @Test
    void namesBecomeSafeShortAddressesAndXmlIsEscaped() {
        assertThat(SeoService.productPath(5, "  Mug & Cup (Blue)! ")).isEqualTo("/products/5/mug-cup-blue");
        assertThat(SeoService.productPath(5, "!!!")).isEqualTo("/products/5");
        assertThat(SeoService.productPath(5, null)).isEqualTo("/products/5");
        assertThat(SeoService.productPath(5, "a".repeat(79) + " b")).isEqualTo("/products/5/" + "a".repeat(79));
        assertThat(SeoService.productPath(5, "x".repeat(100))).hasSize("/products/5/".length() + 80);
        assertThat(SeoService.escape("a&b<c>\"d'")).isEqualTo("a&amp;b&lt;c&gt;&quot;d&apos;");
        String xml = shop(new SeoService.Item(1, "x", null, "https://cdn.example.com/a.jpg?x=1&y=2")).sitemap();
        assertThat(xml).contains("a.jpg?x=1&amp;y=2").doesNotContain("&y=");
    }
}
