package com.rbctcsworld.ecommerce.integration;

import com.rbctcsworld.ecommerce.TestAddresses;
import com.rbctcsworld.ecommerce.common.exception.PaymentUnavailableException;
import com.rbctcsworld.ecommerce.tax.TaxCalculator;
import com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxQuote;
import com.rbctcsworld.ecommerce.tax.TaxCalculator.TaxRequest;
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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tax from a tax provider (Stripe Tax) through the API and the database. The provider is a Mockito bean that
 * answers like Stripe Tax: an estimate without an address, 8 % with one (its HTTP layer is covered by
 * StripeTaxClientTest and the stripe-mock contract test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StripeTaxIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TaxCalculator taxes;

    private String admin;
    private String customer;

    @BeforeEach
    void setUp() throws Exception {
        when(taxes.name()).thenReturn("stripe");
        when(taxes.calculate(any())).thenAnswer(i -> {
            TaxRequest r = i.getArgument(0);
            BigDecimal eight = r.merchandise().multiply(new BigDecimal("0.08")).setScale(2, java.math.RoundingMode.HALF_UP);
            return r.address() == null ? new TaxQuote(eight, null, true)
                    : new TaxQuote(eight, "taxcalc_" + r.address().zip(), false);
        });
        admin = token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@rbctcsworld.com\",\"password\":\"Admin@12345\"}")));
        customer = token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"tax-" + UUID.randomUUID() + "@test.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated()));
        long product = mapper.readTree(mvc.perform(as(post("/api/products"), admin).content("{\"name\":\"Tax IT\",\"sku\":\"TAX-"
                        + UUID.randomUUID().toString().substring(0, 8) + "\",\"price\":10.00,\"stock\":5}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(as(post("/api/cart/items"), customer).content("{\"productId\":" + product + ",\"quantity\":2}"))
                .andExpect(status().isCreated());
    }

    private String token(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void theQuoteSaysWhenTheTaxIsAnEstimateAndIsExactWithTheAddress() throws Exception {
        mvc.perform(as(post("/api/checkout/quote"), customer).content("{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tax").value(1.60))
                .andExpect(jsonPath("$.taxEstimated").value(true));
        mvc.perform(as(post("/api/checkout/quote"), customer).content(TestAddresses.ORDER_BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tax").value(1.60))
                .andExpect(jsonPath("$.total").value(27.59))                    // 20.00 + 5.99 + 1.60
                .andExpect(jsonPath("$.taxEstimated").value(false));
    }

    @Test
    void anOrderKeepsTheProvidersTaxAndThePaymentRecordsTheSale() throws Exception {
        String body = mvc.perform(as(post("/api/orders"), customer).content(TestAddresses.ORDER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tax").value(1.60))
                .andExpect(jsonPath("$.total").value(27.59))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(body).get("id").asLong();
        String number = mapper.readTree(body).get("orderNumber").asString();
        assertThat(jdbc.queryForObject("select tax_calculation_id from orders where id = ?", String.class, id))
                .isEqualTo("taxcalc_15213");
        verify(taxes, never()).record(anyString(), anyString());                // not before the money moved

        mvc.perform(as(post("/api/orders/" + id + "/pay"), customer)
                        .content("{\"cardNumber\":\"4242424242424242\",\"expiryMonth\":12,\"expiryYear\":2035,\"cvv\":\"123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        verify(taxes, timeout(5000)).record("taxcalc_15213", number);              // after the commit, off the request
    }

    /** The value once the background work has saved it (at most 5 s). */
    private String column(String name, long id) throws InterruptedException {
        String value = null;
        for (int i = 0; i < 50 && value == null; i++) {
            value = jdbc.queryForObject("select " + name + " from orders where id = ?", String.class, id);
            if (value == null) Thread.sleep(100);
        }
        return value;
    }

    @Test
    void aCancelledPaidOrderReversesTheWholeSaleSoTheRefundedTaxIsNotFiled() throws Exception {
        when(taxes.record(anyString(), anyString())).thenReturn("tax_sale_1");
        when(taxes.reverse(anyString(), anyString(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn("tax_reversal_1");
        String body = mvc.perform(as(post("/api/orders"), customer).content(TestAddresses.ORDER_BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(body).get("id").asLong();
        String number = mapper.readTree(body).get("orderNumber").asString();
        mvc.perform(as(post("/api/orders/" + id + "/pay"), customer)
                        .content("{\"cardNumber\":\"4242424242424242\",\"expiryMonth\":12,\"expiryYear\":2035,\"cvv\":\"123\"}"))
                .andExpect(status().isOk());
        assertThat(column("tax_transaction_id", id)).isEqualTo("tax_sale_1");
        verify(taxes, never()).reverse(anyString(), anyString(), any(), org.mockito.ArgumentMatchers.anyBoolean());

        mvc.perform(as(post("/api/orders/" + id + "/cancel"), customer)).andExpect(status().isOk());

        verify(taxes, timeout(5000)).reverse("tax_sale_1", number + "-cancel", new BigDecimal("27.59"), true);
        assertThat(column("tax_reversal_id", id)).isEqualTo("tax_reversal_1");
    }

    @Test
    void anUnpaidOrderCancelledHasNothingToReverse() throws Exception {
        long id = mapper.readTree(mvc.perform(as(post("/api/orders"), customer).content(TestAddresses.ORDER_BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(as(post("/api/orders/" + id + "/cancel"), customer)).andExpect(status().isOk());
        verify(taxes, never()).reverse(anyString(), anyString(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        assertThat(jdbc.queryForObject("select tax_reversal_id from orders where id = ?", String.class, id)).isNull();
    }

    @Test
    void whenTheTaxProviderIsDownNoOrderIsPlacedAndTheCartStays() throws Exception {
        // doThrow: when(taxes.calculate(..)) would run the answer from setUp with a null request while restubbing
        doThrow(new PaymentUnavailableException("Tax calculation is not available right now.")).when(taxes).calculate(any());
        mvc.perform(as(post("/api/orders"), customer).content(TestAddresses.ORDER_BODY))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(as(get("/api/cart"), customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(2));
    }
}
