package com.rbctcsworld.ecommerce.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full Spring context + real Flyway migrations on H2 (PostgreSQL mode) + real security filter.
 * Proves the HTTP contract (status codes and JSON) without Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    private static final String PASSWORD = "Password1!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    // ---------- helpers ----------

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asString();
    }

    private String newCustomerToken() throws Exception {
        return register("cust-" + UUID.randomUUID() + "@test.com");
    }

    private String adminToken() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("admin@rbctcsworld.com", "Admin@12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asString();
    }

    private static String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions addToCart(String token, long productId, int qty) throws Exception {
        return mvc.perform(auth(post("/api/cart/items"), token)
                .content("{\"productId\":" + productId + ",\"quantity\":" + qty + "}"));
    }

    private long createProduct(String admin, String sku, int stock) throws Exception {
        String body = mvc.perform(auth(post("/api/products"), admin).content(
                        "{\"name\":\"Test " + sku + "\",\"sku\":\"" + sku + "\",\"category\":\"test\",\"price\":10.00,\"stock\":" + stock + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(body);
        return node.get("id").asLong();
    }

    private static String sku() {
        return "IT-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------- auth ----------

    @Test
    void duplicateRegistrationIs409() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        register(email);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(email, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("admin@rbctcsworld.com", "WrongPass123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidEmailAndShortPasswordAre400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json("not-an-email", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // ---------- products & roles ----------

    @Test
    void catalogIsPublicAndSeeded() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(8)));
    }

    @Test
    void catalogIsPaginatedWithTotalsAndLinks_DEF007() throws Exception {
        mvc.perform(get("/api/products?size=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(header().string("X-Page", "0"))
                .andExpect(header().string("X-Page-Size", "3"))
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(header().string("Link", org.hamcrest.Matchers.containsString("page=1>; rel=\"next\"")));
        mvc.perform(get("/api/products?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products?sort=stock")).andExpect(status().isBadRequest());
    }

    @Test
    void missingProductIs404NotA500() throws Exception {
        mvc.perform(get("/api/products/999999")).andExpect(status().isNotFound());
    }

    @Test
    void anonymousCannotCreateProduct401() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"sku\":\"x\",\"price\":1,\"stock\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateProduct403() throws Exception {
        mvc.perform(auth(post("/api/products"), newCustomerToken())
                        .content("{\"name\":\"x\",\"sku\":\"x\",\"price\":1,\"stock\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdateChangesAllFieldsAndSoftDeleteHidesProduct() throws Exception {
        String admin = adminToken();
        long id = createProduct(admin, sku(), 5);

        mvc.perform(auth(put("/api/products/" + id), admin).content(
                        "{\"name\":\"Renamed\",\"sku\":\"" + sku() + "\",\"category\":\"books\",\"price\":99.99,\"stock\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.price").value(99.99))
                .andExpect(jsonPath("$.stock").value(7));

        mvc.perform(auth(delete("/api/products/" + id), admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateSkuIs409() throws Exception {
        String admin = adminToken();
        String sku = sku();
        createProduct(admin, sku, 1);
        mvc.perform(auth(post("/api/products"), admin)
                        .content("{\"name\":\"x\",\"sku\":\"" + sku + "\",\"price\":1,\"stock\":1}"))
                .andExpect(status().isConflict());
    }

    // ---------- cart (Module 1) ----------

    @Test
    void cartRequiresToken401() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addUpdateRemoveFlow() throws Exception {
        long productId = createProduct(adminToken(), sku(), 5);
        String token = newCustomerToken();

        mvc.perform(auth(get("/api/cart"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.subtotal").value(0));

        addToCart(token, productId, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.subtotal").value(20.00));

        // same product again -> merged into one line
        String body = addToCart(token, productId, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andReturn().getResponse().getContentAsString();
        long itemId = mapper.readTree(body).at("/items/0/itemId").asLong();

        mvc.perform(auth(put("/api/cart/items/" + itemId), token).content("{\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(5))
                .andExpect(jsonPath("$.subtotal").value(50.00));

        mvc.perform(auth(delete("/api/cart/items/" + itemId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void quantityAboveStockIs409() throws Exception {
        long productId = createProduct(adminToken(), sku(), 3);
        addToCart(newCustomerToken(), productId, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("available 3")));
    }

    @Test
    void invalidQuantityIs400() throws Exception {
        String token = newCustomerToken();
        addToCart(token, 1, 0).andExpect(status().isBadRequest());
        addToCart(token, 1, 11).andExpect(status().isBadRequest());
    }

    @Test
    void unknownProductIs404() throws Exception {
        addToCart(newCustomerToken(), 999999, 1).andExpect(status().isNotFound());
    }

    @Test
    void customerCannotTouchAnotherCustomersCartItem_IDOR() throws Exception {
        long productId = createProduct(adminToken(), sku(), 10);
        String alice = newCustomerToken();
        String bob = newCustomerToken();

        String body = addToCart(alice, productId, 1).andReturn().getResponse().getContentAsString();
        long aliceItem = mapper.readTree(body).at("/items/0/itemId").asLong();

        mvc.perform(auth(put("/api/cart/items/" + aliceItem), bob).content("{\"quantity\":2}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/cart/items/" + aliceItem), bob))
                .andExpect(status().isNotFound());

        // Alice's cart is untouched
        mvc.perform(auth(get("/api/cart"), alice))
                .andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void clearCartIs204() throws Exception {
        String token = newCustomerToken();
        addToCart(token, 1, 1).andExpect(status().isCreated());
        mvc.perform(auth(delete("/api/cart"), token)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/cart"), token)).andExpect(jsonPath("$.items.length()").value(0));
    }

    // ---------- product pictures ----------

    @Test
    void seededProductsHaveTheirPictures() throws Exception {
        mvc.perform(get("/api/products").param("q", "Wireless Mouse")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("ELEC-MOUSE-001"))
                .andExpect(jsonPath("$[0].imageUrl").value("/products/elec-mouse-001.svg"));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "image {0} -> 400")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"javascript:alert(1)", "http://plain.example.com/a.png",
            "data:image/png;base64,AAAA", "/products/../secret.svg", "https://a.com/x\\\" onerror=\\\"x"})
    void unsafePictureLinksAre400(String url) throws Exception {
        mvc.perform(auth(post("/api/products"), adminToken()).content("{\"name\":\"Pic\",\"sku\":\"PIC-"
                        + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":1.00,\"stock\":1,\"imageUrl\":\"" + url + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.imageUrl").exists());
    }

    @Test
    void adminCanGiveAProductAPicture() throws Exception {
        String sku = "PIC-" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(auth(post("/api/products"), adminToken()).content("{\"name\":\"Pic\",\"sku\":\"" + sku
                        + "\",\"price\":1.00,\"stock\":1,\"imageUrl\":\"https://cdn.example.com/pic.webp\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.example.com/pic.webp"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(body).get("id").asLong();
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.imageUrl").value("https://cdn.example.com/pic.webp"));
    }
}
