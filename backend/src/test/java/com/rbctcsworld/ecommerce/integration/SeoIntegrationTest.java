package com.rbctcsworld.ecommerce.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** robots.txt and sitemap.xml from the real catalog: REQ-SEO-01..02 (ADR-0019). Development address: not indexed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeoIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void aDevelopmentShopTellsSearchEnginesToStayAway() throws Exception {
        mvc.perform(get("/api/seo/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("Disallow: /\n")))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")));
    }

    @Test
    void theSitemapListsTheSeededCatalogOncePerItem() throws Exception {
        String xml = mvc.perform(get("/api/seo/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andReturn().getResponse().getContentAsString();
        assertThat(xml).contains("<loc>http://localhost:5173/</loc>", "<loc>http://localhost:5173/contact</loc>")
                .containsPattern("<loc>http://localhost:5173/products/\\d+/[a-z0-9-]+</loc>")
                .containsPattern("<image:loc>http://localhost:5173/products/[a-z0-9-]+\\.svg</image:loc>");
        // the seeded T-shirt has eight sizes and colours in one variant group: a single address
        assertThat(xml.split("/classic-cotton-t-shirt<", -1).length - 1).isEqualTo(1);
    }
}
