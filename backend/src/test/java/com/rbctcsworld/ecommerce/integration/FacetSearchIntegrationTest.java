package com.rbctcsworld.ecommerce.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Faceted search through the real API and database: REQ-CAT-11..12. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FacetSearchIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;
    private String tag;
    private String category;

    @BeforeEach
    void setUp() throws Exception {
        admin = mapper.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asString();
        tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        category = "fx" + tag.toLowerCase();
        // two single products and a T-shirt with three variants: three items in a category of their own
        create("Book " + tag, "FX-A-" + tag, null, null, null, "10.00", 5);
        create("Atlas " + tag, "FX-B-" + tag, null, null, null, "60.00", 0);
        create("Tee " + tag, "FX-T1-" + tag, "FXTEE" + tag, "S", "Navy", "19.99", 3);
        create("Tee " + tag, "FX-T2-" + tag, "FXTEE" + tag, "M", "Navy", "19.99", 0);
        create("Tee " + tag, "FX-T3-" + tag, "FXTEE" + tag, "XL", "Grey", "21.99", 4);
        jdbc.update("UPDATE products SET rating_average = 4.5, rating_count = 2 WHERE sku = ?", "FX-A-" + tag);
        jdbc.update("UPDATE products SET rating_average = 3.0, rating_count = 1 WHERE sku = ?", "FX-B-" + tag);
    }

    private void create(String name, String sku, String group, String size, String color, String price, int stock) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"sku\":\"" + sku + "\",\"category\":\"" + category + "\",\"price\":" + price
                + ",\"stock\":" + stock + (group == null ? "" : ",\"variantGroup\":\"" + group + "\"")
                + (size == null ? "" : ",\"size\":\"" + size + "\"") + (color == null ? "" : ",\"color\":\"" + color + "\"") + "}";
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private JsonNode facets(String query) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/products/facets?category=" + category + query))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static long count(JsonNode options, String value) {
        for (JsonNode o : options) if (o.get("value").asString().equals(value)) return o.get("count").asLong();
        return 0;
    }

    @Test
    void theFacetsCountItemsNotVariants() throws Exception {
        JsonNode f = facets("");
        assertThat(f.get("total").asLong()).isEqualTo(3);                      // 2 books + 1 T-shirt
        assertThat(count(f.get("categories"), category)).isEqualTo(3);
        assertThat(f.get("inStock").asLong()).isEqualTo(2);                    // the atlas has none left
        assertThat(count(f.get("colors"), "Navy")).isEqualTo(1);              // two Navy variants, one item
        assertThat(count(f.get("colors"), "Grey")).isEqualTo(1);
        assertThat(count(f.get("sizes"), "S")).isEqualTo(1);
        assertThat(f.get("prices").get(0).get("key").asString()).isEqualTo("under-25");
        assertThat(f.get("prices").get(0).get("count").asLong()).isEqualTo(2);  // the 10.00 book and the tee
        assertThat(f.get("prices").get(2).get("count").asLong()).isEqualTo(1);  // the 60.00 atlas
        assertThat(f.get("ratings").get(0).get("min").asInt()).isEqualTo(4);
        assertThat(f.get("ratings").get(0).get("count").asLong()).isEqualTo(1);
        assertThat(f.get("ratings").get(1).get("count").asLong()).isEqualTo(2); // 3 stars and up: book and atlas
    }

    @Test
    void aFacetIgnoresItsOwnSelectionButEveryOtherFilterApplies() throws Exception {
        JsonNode grey = facets("&colors=Grey");
        assertThat(grey.get("total").asLong()).isEqualTo(1);
        assertThat(count(grey.get("colors"), "Navy")).isEqualTo(1);           // the colour facet ignores "Grey"
        assertThat(count(grey.get("sizes"), "XL")).isEqualTo(1);
        assertThat(count(grey.get("sizes"), "S")).isZero();                    // S is Navy only
        JsonNode stocked = facets("&inStock=true");
        assertThat(stocked.get("total").asLong()).isEqualTo(2);
        assertThat(stocked.get("inStock").asLong()).isEqualTo(2);              // not narrowed by its own box
    }

    @Test
    void theListUsesTheSameFilters() throws Exception {
        mvc.perform(get("/api/products?grouped=true&category=" + category)).andExpect(header().string("X-Total-Count", "3"));
        mvc.perform(get("/api/products?grouped=true&category=" + category + "&colors=Grey,Red")).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].variantGroup").value("FXTEE" + tag));
        mvc.perform(get("/api/products?grouped=true&category=" + category + "&minRating=4")).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].sku").value("FX-A-" + tag));
        mvc.perform(get("/api/products?grouped=true&category=" + category + "&sizes=m")).andExpect(header().string("X-Total-Count", "1"));
        mvc.perform(get("/api/products?grouped=true&category=" + category + "&minPrice=50&maxPrice=99.99")).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].sku").value("FX-B-" + tag));
        mvc.perform(get("/api/products?grouped=true&category=" + category + "&inStock=true&colors=Navy")).andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    void badFiltersAreRefusedWith400() throws Exception {
        mvc.perform(get("/api/products/facets?minRating=6")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("minRating must be between 0 and 5"));
        mvc.perform(get("/api/products?minRating=-1")).andExpect(status().isBadRequest());
        String many = String.join(",", java.util.stream.IntStream.rangeClosed(1, 21).mapToObj(i -> "c" + i).toList());
        mvc.perform(get("/api/products?category=" + many)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("at most 20 values per filter"));
        mvc.perform(get("/api/products/facets?colors=" + many)).andExpect(status().isBadRequest());
    }

    @Test
    void theFacetsArePublic() throws Exception {
        mvc.perform(get("/api/products/facets")).andExpect(status().isOk()).andExpect(jsonPath("$.prices.length()").value(5));
    }
}
