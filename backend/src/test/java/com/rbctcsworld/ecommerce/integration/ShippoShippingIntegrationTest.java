package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.TestAddresses;
import com.rbctcsworld.ecommerce.common.exception.ServiceUnavailableException;
import com.rbctcsworld.ecommerce.fulfillment.ShippingProvider;
import com.rbctcsworld.ecommerce.fulfillment.ShippingProvider.Label;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shipping labels and carrier updates through the API and the database. Shippo is a Mockito bean (its HTTP layer is
 * covered by ShippoClientTest): every label is a USPS label for 7.58 with the tracking number 94001118992231974<order id>.
 */
@SpringBootTest(properties = "app.shipping.shippo.webhook-token=" + ShippoShippingIntegrationTest.TOKEN)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShippoShippingIntegrationTest {

    static final String TOKEN = "shippo-webhook-it";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ShippingProvider shipping;

    private String admin;
    private String customer;

    @BeforeEach
    void setUp() throws Exception {
        when(shipping.name()).thenReturn("shippo");
        when(shipping.buyLabel(any())).thenAnswer(i -> {
            ShippingProvider.LabelRequest r = i.getArgument(0);
            return new Label("94001118992231974" + r.orderNumber().hashCode(), "https://deliver.goshippo.com/" + r.orderNumber() + ".pdf",
                    new BigDecimal("7.58"), "txn_" + r.orderNumber());
        });
        admin = token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}")));
        customer = token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ship-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated()));
    }

    private String token(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private long paidOrder() throws Exception {
        long product = mapper.readTree(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Ship IT\",\"sku\":\"SHP-"
                        + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":10.00,\"stock\":5}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + product + ",\"quantity\":3}"))
                .andExpect(status().isCreated());
        long id = mapper.readTree(mvc.perform(as(post("/api/orders"), customer).content(TestAddresses.ORDER_BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(as(post("/api/orders/" + id + "/pay"), customer)
                        .content("{\"cardNumber\":\"4242424242424242\",\"expiryMonth\":12,\"expiryYear\":2035,\"cvv\":\"123\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions ship(long id, String carrier) throws Exception {
        return mvc.perform(as(post("/api/admin/orders/" + id + "/ship"), admin).content("{\"carrier\":\"" + carrier + "\"}"));
    }

    private ResultActions webhook(String token, String trackingNumber) throws Exception {
        return mvc.perform(post("/api/shipping/shippo/webhook" + (token == null ? "" : "?token=" + token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"track_updated\",\"data\":{\"carrier\":\"usps\",\"tracking_number\":\"" + trackingNumber
                        + "\",\"tracking_status\":{\"status\":\"DELIVERED\"}}}"));
    }

    private String orderStatus(long id) {
        return jdbc.queryForObject("select status from orders where id = ?", String.class, id);
    }

    @Test
    void shippingBuysTheLabelKeepsItAndTheAdminCanPrintIt() throws Exception {
        long id = paidOrder();
        String tracking = mapper.readTree(ship(id, "USPS").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.carrier").value("USPS"))
                .andReturn().getResponse().getContentAsString()).get("trackingNumber").asString();
        assertThat(tracking).startsWith("94001118992231974");

        mvc.perform(as(get("/api/admin/orders/" + id + "/label"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.carrier").value("USPS"))
                .andExpect(jsonPath("$.trackingNumber").value(tracking))
                .andExpect(jsonPath("$.labelUrl").value(org.hamcrest.Matchers.endsWith(".pdf")))
                .andExpect(jsonPath("$.cost").value(7.58))
                .andExpect(jsonPath("$.provider").value("shippo"));
        mvc.perform(as(get("/api/admin/orders/" + id + "/label"), customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/tracking/" + tracking)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));                 // the real number works on the public page
    }

    @Test
    void whenShippoIsDownNoLabelIsBoughtAndTheOrderStaysPaid() throws Exception {
        long id = paidOrder();
        doThrow(new ServiceUnavailableException("The shipping service is not available right now.")).when(shipping).buyLabel(any());
        ship(id, "UPS").andExpect(status().isServiceUnavailable());
        assertThat(orderStatus(id)).isEqualTo("PAID");
        mvc.perform(as(get("/api/admin/orders/" + id + "/label"), admin)).andExpect(status().isNotFound());
    }

    @Test
    void anUnpaidOrderIsNeverSentToTheCarrier() throws Exception {
        long product = mapper.readTree(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Ship IT\",\"sku\":\"SHU-"
                        + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":10.00,\"stock\":5}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + product + ",\"quantity\":1}"));
        long id = mapper.readTree(mvc.perform(as(post("/api/orders"), customer).content(TestAddresses.ORDER_BODY))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        ship(id, "UPS").andExpect(status().isConflict());
        verify(shipping, never()).buyLabel(any());
    }

    @Test
    void theCarriersDeliveredReportDeliversTheOrderOnlyWithTheTokenAndOnlyIfShippoConfirms() throws Exception {
        long id = paidOrder();
        String tracking = mapper.readTree(ship(id, "USPS").andReturn().getResponse().getContentAsString())
                .get("trackingNumber").asString();

        webhook(null, tracking).andExpect(status().isBadRequest());
        webhook("wrong", tracking).andExpect(status().isBadRequest());
        verify(shipping, never()).trackingStatus(anyString(), anyString());

        when(shipping.trackingStatus("USPS", tracking)).thenReturn("TRANSIT");
        webhook(TOKEN, tracking).andExpect(status().isOk()).andExpect(jsonPath("$.received").value(true));
        assertThat(orderStatus(id)).isEqualTo("SHIPPED");                              // the body said DELIVERED; Shippo did not

        when(shipping.trackingStatus("USPS", tracking)).thenReturn("DELIVERED");
        webhook(TOKEN, tracking).andExpect(status().isOk());
        assertThat(orderStatus(id)).isEqualTo("DELIVERED");
        webhook(TOKEN, tracking).andExpect(status().isOk());                       // a repeat changes nothing
        webhook(TOKEN, "UNKNOWN-PARCEL").andExpect(status().isOk());
        assertThat(orderStatus(id)).isEqualTo("DELIVERED");
    }

    // ---------- REQ-FUL-06: the product's own weight and box size go to the label ----------

    private ResultActions createProduct(String extra) throws Exception {
        return mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Boxed IT\",\"sku\":\"BOX-"
                + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":10.00,\"stock\":5" + extra + "}"));
    }

    @Test
    void aProductsWeightAndBoxSizeAreKeptAndGoToTheLabel() throws Exception {
        String body = createProduct(",\"weightOz\":28,\"lengthIn\":13,\"widthIn\":8,\"heightIn\":4.5")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weightOz").value(28.0))
                .andExpect(jsonPath("$.lengthIn").value(13.0))
                .andExpect(jsonPath("$.heightIn").value(4.5))
                .andReturn().getResponse().getContentAsString();
        long product = mapper.readTree(body).get("id").asLong();
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + product + ",\"quantity\":1}"))
                .andExpect(status().isCreated());
        long id = mapper.readTree(mvc.perform(as(post("/api/orders"), customer).content(TestAddresses.ORDER_BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(as(post("/api/orders/" + id + "/pay"), customer)
                        .content("{\"cardNumber\":\"4242424242424242\",\"expiryMonth\":12,\"expiryYear\":2035,\"cvv\":\"123\"}"))
                .andExpect(status().isOk());

        ship(id, "USPS").andExpect(status().isOk());

        org.mockito.ArgumentCaptor<ShippingProvider.LabelRequest> asked = org.mockito.ArgumentCaptor.forClass(ShippingProvider.LabelRequest.class);
        verify(shipping).buyLabel(asked.capture());
        assertThat(asked.getValue().pieces()).hasSize(1);
        ShippingProvider.Piece piece = asked.getValue().pieces().get(0);
        assertThat(piece.quantity()).isEqualTo(1);
        assertThat(piece.weightOz()).isEqualByComparingTo("28");
        assertThat(piece.lengthIn()).isEqualByComparingTo("13");
        assertThat(piece.widthIn()).isEqualByComparingTo("8");
        assertThat(piece.heightIn()).isEqualByComparingTo("4.5");
    }

    @Test
    void aProductWithoutWeightOrSizeShowsNoneAndShipsWithTheDefaults() throws Exception {
        createProduct("").andExpect(status().isCreated())
                .andExpect(jsonPath("$.weightOz").doesNotExist())
                .andExpect(jsonPath("$.lengthIn").doesNotExist());
    }

    @Test
    void impossibleWeightsAndSizesAre400() throws Exception {
        createProduct(",\"lengthIn\":13,\"widthIn\":8").andExpect(status().isBadRequest());     // two sides
        createProduct(",\"weightOz\":0").andExpect(status().isBadRequest());
        createProduct(",\"weightOz\":2401").andExpect(status().isBadRequest());                   // over 150 lb
        createProduct(",\"weightOz\":12.25").andExpect(status().isBadRequest());                  // one decimal
        createProduct(",\"lengthIn\":109,\"widthIn\":8,\"heightIn\":4").andExpect(status().isBadRequest());
        createProduct(",\"lengthIn\":13,\"widthIn\":-8,\"heightIn\":4").andExpect(status().isBadRequest());
    }

    // ---------- REQ-RET-04: a prepaid return label for an approved return ----------

    @Test
    void anApprovedReturnGetsAPrepaidLabelTheCustomerCanPrintOnce() throws Exception {
        when(shipping.buyReturnLabel(any())).thenAnswer(i -> {
            ShippingProvider.LabelRequest r = i.getArgument(0);
            return new ShippingProvider.ReturnLabel("USPS", "9205590164917312751089",
                    "https://deliver.goshippo.com/return-" + r.orderNumber() + ".pdf", new BigDecimal("6.95"), "txn_r_" + r.orderNumber());
        });
        long id = paidOrder();
        ship(id, "USPS").andExpect(status().isOk());
        mvc.perform(as(post("/api/admin/orders/" + id + "/deliver"), admin)).andExpect(status().isOk());
        mvc.perform(as(post("/api/orders/" + id + "/return"), customer).content("{\"reason\":\"WRONG_ITEM\"}"))
                .andExpect(status().isCreated());
        long returnId = jdbc.queryForObject("select id from return_requests where order_id = ?", Long.class, id);
        mvc.perform(as(post("/api/admin/returns/" + returnId + "/label"), admin)).andExpect(status().isConflict()); // not approved yet
        mvc.perform(as(post("/api/admin/returns/" + returnId + "/approve"), admin)).andExpect(status().isOk());

        mvc.perform(as(post("/api/admin/returns/" + returnId + "/label"), customer)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/admin/returns/" + returnId + "/label"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.returnCarrier").value("USPS"))
                .andExpect(jsonPath("$.returnTrackingNumber").value("9205590164917312751089"));

        org.mockito.ArgumentCaptor<ShippingProvider.LabelRequest> asked = org.mockito.ArgumentCaptor.forClass(ShippingProvider.LabelRequest.class);
        verify(shipping).buyReturnLabel(asked.capture());
        assertThat(asked.getValue().to().zip()).isEqualTo("15213");                  // from the customer's address
        assertThat(asked.getValue().items()).isEqualTo(3);

        mvc.perform(as(get("/api/orders/" + id + "/return"), customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.returnLabelUrl").value(org.hamcrest.Matchers.endsWith(".pdf")))
                .andExpect(jsonPath("$.returnLabelCost").doesNotExist())            // the store's cost stays private
                .andExpect(jsonPath("$.returnLabelRef").doesNotExist());
        assertThat(jdbc.queryForObject("select return_label_cost from return_requests where id = ?", BigDecimal.class, returnId))
                .isEqualByComparingTo("6.95");
        assertThat(jdbc.queryForObject("select count(*) from notifications where order_id = ? and type = 'RETURN_LABEL_READY'",
                Long.class, id)).isEqualTo(1);

        mvc.perform(as(post("/api/admin/returns/" + returnId + "/label"), admin)).andExpect(status().isConflict()); // only once
        mvc.perform(as(post("/api/admin/returns/99999999/label"), admin)).andExpect(status().isNotFound());
    }
}
