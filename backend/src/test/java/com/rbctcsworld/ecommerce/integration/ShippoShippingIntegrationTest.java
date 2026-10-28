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
}
