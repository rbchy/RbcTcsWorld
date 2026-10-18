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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Product variants (size / colour) through the real API and database: REQ-CAT-07..10. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductVariantIntegrationTest {

    private static final String ADDRESS = "{\"shippingAddress\":{\"fullName\":\"Ada Lovelace\",\"line1\":\"2 Oak Ave\","
            + "\"city\":\"Erie\",\"state\":\"PA\",\"zip\":\"16501\",\"phone\":\"814-555-0100\"}}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;
    private String tag;

    @BeforeEach
    void setUp() throws Exception {
        admin = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"))).get("token").asString();
        tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String body(String name, String sku, String group, String size, String color, String price, int stock) {
        return "{\"name\":\"" + name + "\",\"sku\":\"" + sku + "\",\"category\":\"clothing\",\"price\":" + price
                + ",\"stock\":" + stock + (group == null ? "" : ",\"variantGroup\":\"" + group + "\"")
                + (size == null ? "" : ",\"size\":\"" + size + "\"") + (color == null ? "" : ",\"color\":\"" + color + "\"") + "}";
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(as(post("/api/products"), admin).content(json));
    }

    @Test
    void theSeededTeeHasTwoColoursAndFourSizesAndItsStockIsInTheWarehouses() throws Exception {
        JsonNode all = json(mvc.perform(get("/api/products").param("q", "Classic Cotton T-Shirt").param("size", "100"))
                .andExpect(status().isOk()));
        assertThat(all.size()).isEqualTo(8);
        long first = all.get(0).get("id").asLong();
        assertThat(all.get(0).get("variantGroup").asString()).isEqualTo("TEE-CLASSIC");

        mvc.perform(get("/api/products").param("q", "Classic Cotton T-Shirt").param("grouped", "true"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(first));

        JsonNode variants = json(mvc.perform(get("/api/products/" + all.get(5).get("id").asLong() + "/variants"))
                .andExpect(status().isOk()));
        assertThat(variants.size()).isEqualTo(8);
        int units = 0;
        for (JsonNode v : variants) units += v.get("stock").asInt();

        mvc.perform(get("/api/products/variant-groups").param("names", "tee-classic", "NO-SUCH-GROUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].group").value("TEE-CLASSIC"))
                .andExpect(jsonPath("$[0].options").value(8))
                .andExpect(jsonPath("$[0].colors[0]").value("Navy"))
                .andExpect(jsonPath("$[0].colors[1]").value("Heather Grey"))
                .andExpect(jsonPath("$[0].sizes.length()").value(4))
                .andExpect(jsonPath("$[0].sizes[0]").value("S"))
                .andExpect(jsonPath("$[0].sizes[3]").value("XL"))
                .andExpect(jsonPath("$[0].minPrice").value(19.99))
                .andExpect(jsonPath("$[0].maxPrice").value(21.99))
                .andExpect(jsonPath("$[0].stock").value(units));

        Integer inWarehouses = jdbc.queryForObject("select coalesce(sum(w.quantity), 0) from warehouse_stock w "
                + "join products p on p.id = w.product_id where p.variant_group = 'TEE-CLASSIC'", Integer.class);
        Integer total = jdbc.queryForObject("select sum(stock) from products where variant_group = 'TEE-CLASSIC'", Integer.class);
        assertThat(inWarehouses).isEqualTo(total);
    }

    @Test
    void theAdminBuildsAGroupWithTheSameRulesAsTheForm() throws Exception {
        String group = "HOOD-" + tag;
        String name = "Hoodie " + tag;
        long red = json(create(body(name, group + "-R-M", group.toLowerCase(), "M", "Red", "39.00", 4))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variantGroup").value(group))
                .andExpect(jsonPath("$.size").value("M"))
                .andExpect(jsonPath("$.color").value("Red"))).get("id").asLong();
        create(body(name, group + "-B-M", group, "M", "Black", "39.00", 2)).andExpect(status().isCreated());

        create(body(name, group + "-X", group, "m", "RED", "39.00", 1))                       // same size + colour
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already has")));
        create(body(name, group + "-Y", null, "M", null, "39.00", 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A size or colour needs a variant group"));
        create(body(name, group + "-Z", group, null, null, "39.00", 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A variant needs a size, a colour or both"));
        create(body(name, group + "-Q", "-bad group", "M", "Red", "39.00", 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.variantGroup").exists());
        create(body(name, group + "-W", group, "M<script>", "Red5", "39.00", 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").exists())
                .andExpect(jsonPath("$.fieldErrors.color").exists());

        mvc.perform(get("/api/products").param("q", name)).andExpect(header().string("X-Total-Count", "2"));
        mvc.perform(get("/api/products").param("q", name).param("grouped", "true"))
                .andExpect(header().string("X-Total-Count", "1")).andExpect(jsonPath("$[0].id").value(red));

        // the first variant is deleted: the next one becomes the group's card
        mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/products/" + red), admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products").param("q", name).param("grouped", "true"))
                .andExpect(header().string("X-Total-Count", "1")).andExpect(jsonPath("$[0].color").value("Black"));

        // a deleted variant frees its size + colour
        create(body(name, group + "-R2", group, "M", "Red", "39.00", 1)).andExpect(status().isCreated());
    }

    @Test
    void anEditCanMoveAVariantButNotOntoAnotherOne() throws Exception {
        String group = "CAP-" + tag;
        long blue = json(create(body("Cap", group + "-1", group, null, "Blue", "12.00", 3))).get("id").asLong();
        create(body("Cap", group + "-2", group, null, "Green", "12.00", 3)).andExpect(status().isCreated());

        mvc.perform(as(put("/api/products/" + blue), admin).content(body("Cap", group + "-1", group, null, "Green", "12.00", 3)))
                .andExpect(status().isConflict());
        mvc.perform(as(put("/api/products/" + blue), admin).content(body("Cap", group + "-1", group, null, "Navy", "12.00", 3)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.color").value("Navy"));

        List<String> fields = new ArrayList<>(jdbc.queryForList("select field from audit_log where entity_type = 'PRODUCT' "
                + "and entity_id = ? and action = 'PRODUCT_UPDATED'", String.class, blue));
        assertThat(fields).containsExactly("color");                      // the audit trail names exactly what changed
    }

    @Test
    void theCartTheOrderAndTheWishlistNameTheVariant() throws Exception {
        String group = "SOCK-" + tag;
        long sock = json(create(body("Running Socks", group + "-L", group, "L", "White", "6.50", 5))).get("id").asLong();
        String customer = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"var-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))).get("token").asString();

        mvc.perform(as(post("/api/wishlist"), customer).content("{\"productId\":" + sock + "}")).andExpect(status().isCreated());
        mvc.perform(as(get("/api/wishlist"), customer)).andExpect(jsonPath("$.items[0].name").value("Running Socks (White, L)"));

        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + sock + ",\"quantity\":2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].name").value("Running Socks (White, L)"));
        mvc.perform(as(post("/api/orders"), customer).content(ADDRESS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].name").value("Running Socks (White, L)"))
                .andExpect(jsonPath("$.items[0].sku").value(group + "-L"));
        assertThat(jdbc.queryForObject("select stock from products where id = ?", Integer.class, sock)).isEqualTo(3);
    }

    @Test
    void similarProductsLeaveOutTheOtherSizesOfTheSameItem() throws Exception {
        long tee = json(mvc.perform(get("/api/products").param("q", "Classic Cotton T-Shirt"))).get(0).get("id").asLong();
        JsonNode similar = json(mvc.perform(get("/api/products/" + tee + "/similar").param("limit", "12")).andExpect(status().isOk()));
        for (JsonNode s : similar) assertThat(s.get("variantGroup").isNull() || !"TEE-CLASSIC".equals(s.get("variantGroup").asString())).isTrue();
        mvc.perform(get("/api/products/999999/variants")).andExpect(status().isNotFound());
    }
}
