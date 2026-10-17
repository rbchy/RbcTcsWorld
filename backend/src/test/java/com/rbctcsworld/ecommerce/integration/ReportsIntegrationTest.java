package com.rbctcsworld.ecommerce.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reports and the payment ledger through the real API, after a real purchase. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String admin;
    private String customer;
    private String sku;
    private String category;
    private String orderNumber;

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}"))).get("token").asString();
        customer = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rep-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))).get("token").asString();
        sku = "RP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        category = "rep-" + sku.substring(3).toLowerCase();
        long productId = json(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Report item\",\"sku\":\""
                + sku + "\",\"category\":\"" + category + "\",\"price\":10.00,\"stock\":2}"))).get("id").asLong();
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + productId + ",\"quantity\":1}"))
                .andExpect(status().isCreated());
        JsonNode order = json(mvc.perform(as(post("/api/orders"), customer)
                .content(com.rbctcsworld.ecommerce.TestAddresses.ORDER_BODY)).andExpect(status().isCreated()));
        orderNumber = order.get("orderNumber").asString();
        mvc.perform(as(post("/api/orders/" + order.get("id").asLong() + "/pay"), customer)
                .content("{\"cardNumber\":\"4242424242424242\",\"expiryMonth\":12,\"expiryYear\":2035,\"cvv\":\"123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void todaysSaleShowsUpInSalesTopProductsAndLowStock() throws Exception {
        String today = LocalDate.now().toString();
        JsonNode sales = json(mvc.perform(as(get("/api/admin/reports/sales?from=" + today + "&to=" + today), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0]").value("day"))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.totals[0]").value("total")));
        assertThat(sales.get("rows").get(0).get(1).asLong()).isPositive();

        mvc.perform(as(get("/api/admin/reports/top-products?from=" + today + "&to=" + today + "&limit=100"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[*][0]").value(hasItem(sku)));
        mvc.perform(as(get("/api/admin/reports/low-stock?threshold=1"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[*][0]").value(hasItem(sku)))
                .andExpect(jsonPath("$.totals").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void everyReportAnswersAndExportsAsCsv() throws Exception {
        for (String name : new String[]{"sales", "top-products", "orders-by-status", "returns", "coupons", "low-stock", "inventory", "stock-by-warehouse"}) {
            mvc.perform(as(get("/api/admin/reports/" + name), admin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(name));
        }
        String csv = mvc.perform(as(get("/api/admin/reports/inventory/export"), admin)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("report-inventory.csv")))
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).startsWith("\"category\",\"products\",\"units\",\"value\"\r\n").contains("\"" + category + "\",1,1,10.00");
        mvc.perform(as(get("/api/admin/reports/sales/export?from=2026-01-01&to=2026-01-31"), admin))
                .andExpect(header().string("Content-Disposition", containsString("report-sales-2026-01-01-to-2026-01-31.csv")));
    }

    @Test
    void wrongReportRequestsAreRefusedClearly() throws Exception {
        mvc.perform(as(get("/api/admin/reports"), admin)).andExpect(jsonPath("$.length()").value(8));
        mvc.perform(as(get("/api/admin/reports/salary"), admin)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/admin/reports/sales?from=2026-02-01&to=2026-01-01"), admin)).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/admin/reports/sales?from=yesterday"), admin)).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/admin/reports/sales?groupBy=week"), admin)).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/admin/reports/sales"), customer)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/payments"), customer)).andExpect(status().isForbidden());
    }

    @Test
    void thePaymentLedgerFindsTheChargeByOrderNumber() throws Exception {
        mvc.perform(as(get("/api/admin/payments?q=" + orderNumber.toLowerCase()), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].type").value("CHARGE"))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].cardLast4").value("4242"))
                .andExpect(jsonPath("$.items[0].orderNumber").value(orderNumber));
        mvc.perform(as(get("/api/admin/payments?type=GIFT"), admin)).andExpect(status().isBadRequest());
    }
}
