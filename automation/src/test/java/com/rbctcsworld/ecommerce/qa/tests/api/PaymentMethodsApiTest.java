package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.OrderClient;
import com.rbctcsworld.ecommerce.qa.api.PaymentMethodClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures.Customer;
import com.rbctcsworld.ecommerce.qa.testdata.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/** Saved cards (the wallet) and paying with them (REQ-PAY-08). The card number and the token never come back. */
@Tag("api")
@Tag("payment")
@Epic("Payment")
@Feature("Saved payment methods")
class PaymentMethodsApiTest {

    private final PaymentMethodClient wallet = new PaymentMethodClient();
    private final OrderClient orders = new OrderClient();

    @Test
    @Tag("smoke")
    @DisplayName("Add two cards: first is default, only brand / last 4 / expiry come back, default moves and deleting the default promotes the other")
    void walletBasics() {
        Customer c = Fixtures.newCustomer();
        wallet.add(c.token(), "4242 4242 4242 4242", 12, 2035, false).then().statusCode(201)
                .body("brand", equalTo("Visa")).body("last4", equalTo("4242")).body("isDefault", equalTo(true))
                .body("$", not(hasKey("token"))).body("$", not(hasKey("cardNumber")));
        long second = wallet.add(c.token(), "5555555555554444", 11, 2034, false).then().statusCode(201)
                .body("brand", equalTo("Mastercard")).body("isDefault", equalTo(false)).extract().jsonPath().getLong("id");

        wallet.makeDefault(c.token(), second).then().statusCode(200).body("isDefault", equalTo(true));
        wallet.list(c.token()).then().statusCode(200).body("$", hasSize(2)).body("[0].last4", equalTo("4444"));
        wallet.delete(c.token(), second).then().statusCode(204);
        wallet.list(c.token()).then().body("$", hasSize(1)).body("[0].isDefault", equalTo(true)).body("[0].last4", equalTo("4242"));
    }

    @Test
    @DisplayName("Bad cards are 400, the same card twice and a sixth card are 409, the wallet needs a sign-in")
    void rules() {
        Customer c = Fixtures.newCustomer();
        wallet.add(c.token(), TestData.CARD_BAD_LUHN, 12, 2035, false).then().statusCode(400).body("message", containsString("Invalid card number"));
        wallet.add(c.token(), TestData.CARD_OK, 1, 2020, false).then().statusCode(400).body("message", containsString("expired"));
        wallet.add(c.token(), TestData.CARD_OK, 12, 2035, false).then().statusCode(201);
        wallet.add(c.token(), TestData.CARD_OK, 12, 2035, false).then().statusCode(409).body("message", containsString("already saved"));
        for (String n : new String[] {"5555555555554444", "4000000000000002", "4000000000009995", "378282246310005"}) {
            wallet.add(c.token(), n, 12, 2035, false).then().statusCode(201);
        }
        wallet.add(c.token(), "4000056655665556", 12, 2035, false).then().statusCode(409).body("message", containsString("wallet is full"));
        wallet.list(null).then().statusCode(401);
    }

    @Test
    @DisplayName("Pay with a saved card: PAID, only last 4 recorded; somebody else's card is 404; a saved declining card still declines")
    void payWithSavedCard() {
        Customer c = Fixtures.newCustomer();
        Customer other = Fixtures.newCustomer();
        long card = wallet.add(c.token(), TestData.CARD_OK, 12, 2035, false).then().statusCode(201).extract().jsonPath().getLong("id");
        long declining = wallet.add(c.token(), TestData.CARD_DECLINED, 12, 2035, false).then().statusCode(201).extract().jsonPath().getLong("id");
        long product = Fixtures.product("10.00", 20);

        long order = Fixtures.placedOrder(c, product, 1);
        orders.paySaved(other.token(), order, card, "123").then().statusCode(404);                  // not their order
        orders.paySaved(c.token(), order, declining, "123").then().statusCode(402);                 // declined, order still open
        orders.paySaved(c.token(), order, card, "12").then().statusCode(400);                       // CVV 3-4 digits
        orders.paySaved(c.token(), order, card, "123").then().statusCode(200).body("status", equalTo("PAID"));
        orders.payments(c.token(), order).then().statusCode(200).body("find { it.status == 'SUCCEEDED' }.cardLast4", equalTo("4242"));
        orders.paySaved(c.token(), order, card, "123").then().statusCode(409);                      // no double charge

        long order2 = Fixtures.placedOrder(other, product, 1);
        orders.paySaved(other.token(), order2, card, "123").then().statusCode(404);                 // not their card
    }

    @Test
    @DisplayName("'Save this card' keeps it only after a successful payment; a declined card is not saved")
    void saveCardWithPayment() {
        Customer c = Fixtures.newCustomer();
        long product = Fixtures.product("10.00", 20);
        long order = Fixtures.placedOrder(c, product, 1);

        orders.payAndSave(c.token(), order, TestData.CARD_DECLINED).then().statusCode(402);
        wallet.list(c.token()).then().body("$", hasSize(0));
        orders.payAndSave(c.token(), order, TestData.CARD_OK).then().statusCode(200);
        wallet.list(c.token()).then().body("$", hasSize(1)).body("[0].last4", equalTo("4242"));
    }
}
