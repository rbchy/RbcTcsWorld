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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The audit trail through the real API and database: what is written, who can read it, and that it cannot be changed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditIntegrationTest {

    private static final String ADMIN = "admin@rbctcsworld.com";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String admin;
    private String customer;
    private String customerEmail;
    private String sku;
    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        admin = token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ADMIN + "\",\"password\":\"Admin@12345\"}")));
        customerEmail = "audit-" + UUID.randomUUID() + "@test.com";
        customer = token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + customerEmail + "\",\"password\":\"Password1!\"}")));
        sku = "AU-" + UUID.randomUUID().toString().substring(0, 8);
        productId = json(mvc.perform(as(post("/api/products"), admin).content(product("Audit lamp", "10.00", 10)))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    // ---------- helpers ----------

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String token(ResultActions r) throws Exception {
        return json(r).get("token").asString();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String product(String name, String price, int stock) {
        return "{\"name\":\"" + name + "\",\"sku\":\"" + sku + "\",\"category\":\"home\",\"price\":" + price
                + ",\"stock\":" + stock + "}";
    }

    /** The audit rows of one record, oldest first, as "action field old->new by actor". */
    private List<String> trail(String type, long id) throws Exception {
        JsonNode rows = json(mvc.perform(as(get("/api/admin/audit?entityType=" + type + "&entityId=" + id), admin))
                .andExpect(status().isOk()));
        List<String> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(0, r.get("action").asString() + " " + r.get("field").asString() + " "
                    + (r.get("oldValue").isNull() ? "-" : r.get("oldValue").asString()) + "->"
                    + (r.get("newValue").isNull() ? "-" : r.get("newValue").asString()) + " by "
                    + r.get("actor").asString());
        }
        return out;
    }

    private long paidOrder() throws Exception {
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + productId + ",\"quantity\":1}"))
                .andExpect(status().isCreated());
        long id = json(mvc.perform(as(post("/api/orders"), customer)
                .content(com.rbctcsworld.ecommerce.TestAddresses.ORDER_BODY)).andExpect(status().isCreated()))
                .get("id").asLong();
        mvc.perform(as(post("/api/orders/" + id + "/pay"), customer)
                        .content("{\"cardNumber\":\"4242424242424242\",\"expiryMonth\":12,\"expiryYear\":2035,\"cvv\":\"123\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private void verifyIsValid() throws Exception {
        mvc.perform(as(get("/api/admin/audit/verify"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.brokenAtSeq").value(nullValue()));
    }

    // ---------- tests ----------

    @Test
    void productChangesAreRecordedFieldByFieldWithWhoDidIt() throws Exception {
        mvc.perform(as(put("/api/products/" + productId), admin).content(product("Audit lamp", "12.50", 9)))
                .andExpect(status().isOk());
        mvc.perform(as(put("/api/products/" + productId), admin).content(product("Audit lamp", "12.5", 9)))
                .andExpect(status().isOk());            // same price written differently: nothing to record
        mvc.perform(as(delete("/api/products/" + productId), admin)).andExpect(status().isNoContent());

        assertThat(trail("PRODUCT", productId)).containsExactly(
                "PRODUCT_CREATED name -->Audit lamp by " + ADMIN,
                "PRODUCT_CREATED sku -->" + sku + " by " + ADMIN,
                "PRODUCT_CREATED category -->home by " + ADMIN,
                "PRODUCT_CREATED price -->10.00 by " + ADMIN,
                "PRODUCT_CREATED stock -->10 by " + ADMIN,
                "PRODUCT_CREATED active -->true by " + ADMIN,
                "PRODUCT_UPDATED price 10.00->12.50 by " + ADMIN,
                "PRODUCT_UPDATED stock 10->9 by " + ADMIN,
                "PRODUCT_DELETED active true->false by " + ADMIN);
        verifyIsValid();
    }

    @Test
    void orderStatusChangesAreRecordedAndARefusedChangeIsNot() throws Exception {
        long id = paidOrder();
        mvc.perform(as(post("/api/admin/orders/" + id + "/deliver"), admin)).andExpect(status().isConflict());
        mvc.perform(as(post("/api/admin/orders/" + id + "/ship"), admin).content("{\"carrier\":\"UPS\"}"))
                .andExpect(status().isOk());

        assertThat(trail("ORDER", id)).containsExactly(
                "ORDER_STATUS status PLACED->PAID by " + customerEmail,
                "ORDER_STATUS status PAID->SHIPPED by " + ADMIN);
        verifyIsValid();
    }

    @Test
    void theTrailCanBeFilteredByActor() throws Exception {
        long id = paidOrder();
        JsonNode mine = json(mvc.perform(as(get("/api/admin/audit?actor=" + customerEmail.toUpperCase()), admin))
                .andExpect(status().isOk()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("entityId").asLong()).isEqualTo(id);
        assertThat(mine.get(0).get("hash").asString()).hasSize(64);
        assertThat(mine.get(0).get("seq").asLong()).isPositive();
    }

    @Test
    void theDatabaseRefusesToChangeOrDeleteAuditRows() {
        long seq = jdbc.queryForObject("select max(seq) from audit_log", Long.class);
        assertThatThrownBy(() -> jdbc.update("update audit_log set new_value = '0.01' where seq = ?", seq))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log where seq = ?", seq))
                .hasStackTraceContaining("append-only");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where seq = ?", Integer.class, seq)).isOne();
    }

    @Test
    void verifyFindsARowThatWasChangedBehindTheApplicationsBack() throws Exception {
        long seq = jdbc.queryForObject("select max(seq) from audit_log where entity_type = 'PRODUCT' and entity_id = ?",
                Long.class, productId);
        String original = jdbc.queryForObject("select new_value from audit_log where seq = ?", String.class, seq);
        // someone with database access switches the trigger off and edits a row
        jdbc.execute("drop trigger audit_log_no_change");
        try {
            jdbc.update("update audit_log set new_value = 'tampered' where seq = ?", seq);
            mvc.perform(as(get("/api/admin/audit/verify"), admin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.brokenAtSeq").value((int) seq))
                    .andExpect(jsonPath("$.reason").value("content does not match its hash"));
        } finally {
            jdbc.update("update audit_log set new_value = ? where seq = ?", original, seq);
            jdbc.execute("create trigger audit_log_no_change before update, delete on audit_log for each row "
                    + "call 'com.rbctcsworld.ecommerce.audit.H2AuditAppendOnlyTrigger'");
        }
        verifyIsValid();
    }

    @Test
    void onlyTheStoreAdminCanReadTheTrail() throws Exception {
        mvc.perform(as(get("/api/admin/audit"), customer)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/audit/verify"), customer)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/audit/export"), customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit")).andExpect(status().isUnauthorized());
    }

    @Test
    void aLimitOutsideOneToOneThousandIsRefused() throws Exception {
        mvc.perform(as(get("/api/admin/audit?limit=0"), admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be between 1 and 1000"));
        mvc.perform(as(get("/api/admin/audit?limit=1001"), admin)).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/admin/audit?limit=1"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void theCsvExportIsADownloadWithFormulasDefused() throws Exception {
        mvc.perform(as(put("/api/products/" + productId), admin).content(product("=1+1", "10.00", 10)))
                .andExpect(status().isOk());

        String csv = mvc.perform(as(get("/api/admin/audit/export?entityType=PRODUCT&entityId=" + productId), admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("filename=\"audit-trail.csv\"")))
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn().getResponse().getContentAsString();

        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo("seq,occurred_at,actor,entity_type,entity_id,action,field,old_value,new_value,hash");
        assertThat(lines).hasSize(8);                    // header + 6 created fields + the name change
        assertThat(lines[7]).contains(",\"name\",\"Audit lamp\",\"'=1+1\",");
    }
}
