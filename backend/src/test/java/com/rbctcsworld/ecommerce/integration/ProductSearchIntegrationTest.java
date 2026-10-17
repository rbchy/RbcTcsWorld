package com.rbctcsworld.ecommerce.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Advanced catalog search: category, price range and "in stock only", combined with name search and paging. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductSearchIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String category;
    private String tag;

    @BeforeEach
    void setUp() throws Exception {
        String admin = mapper.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asString();
        tag = UUID.randomUUID().toString().substring(0, 8);
        category = "Cat-" + tag;
        for (String[] p : new String[][]{{"cheap", "5.00", "3"}, {"middle", "20.00", "0"}, {"dear", "80.00", "1"}}) {
            mvc.perform(post("/api/products").header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + p[0] + " " + tag + "\",\"sku\":\"" + p[0] + "-" + tag + "\",\"category\":\""
                            + category + "\",\"price\":" + p[1] + ",\"stock\":" + p[2] + "}"))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void categoryPriceRangeAndStockCombine() throws Exception {
        String base = "/api/products?sort=price_asc&category=" + category.toLowerCase();
        mvc.perform(get(base)).andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$[*].name").value(contains("cheap " + tag, "middle " + tag, "dear " + tag)));
        mvc.perform(get(base + "&minPrice=5.00&maxPrice=20")).andExpect(header().string("X-Total-Count", "2"));
        mvc.perform(get(base + "&inStock=true")).andExpect(jsonPath("$[*].name").value(contains("cheap " + tag, "dear " + tag)));
        mvc.perform(get(base + "&inStock=true&minPrice=10")).andExpect(jsonPath("$[*].name").value(contains("dear " + tag)));
        mvc.perform(get(base + "&q=MIDDLE")).andExpect(jsonPath("$[*].name").value(contains("middle " + tag)));
        mvc.perform(get(base + "&size=1&page=1")).andExpect(jsonPath("$[*].name").value(contains("middle " + tag)))
                .andExpect(header().string("X-Total-Pages", "3"));
        mvc.perform(get("/api/products/categories")).andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasItem(category)));
    }

    @Test
    void aPriceRangeThatMakesNoSenseIsRefused() throws Exception {
        mvc.perform(get("/api/products?minPrice=10&maxPrice=5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("maxPrice must not be below minPrice"));
        mvc.perform(get("/api/products?minPrice=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products?maxPrice=ten")).andExpect(status().isBadRequest());
    }
}
