package com.rbctcsworld.ecommerce.qa.tests.api;

import com.rbctcsworld.ecommerce.qa.api.AddressClient;
import com.rbctcsworld.ecommerce.qa.api.AuthClient;
import com.rbctcsworld.ecommerce.qa.api.CartClient;
import com.rbctcsworld.ecommerce.qa.api.MailpitClient;
import com.rbctcsworld.ecommerce.qa.api.OrderClient;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Guest checkout (REQ-AUTH-15) and the address book (REQ-ADR-01..03) through the HTTP API. */
@Tag("api")
@Tag("checkout")
@Epic("Checkout")
@Feature("Guest checkout and address book")
class CheckoutExtrasApiTest {

    private final AuthClient auth = new AuthClient();
    private final AddressClient addresses = new AddressClient();

    @Test
    @Tag("smoke")
    @DisplayName("A guest buys without a password; the same e-mail again is told to use Forgot password")
    void guestCheckout() {
        String email = TestData.uniqueEmail();
        String token = auth.guest(email).then().statusCode(201).body("guest", equalTo(true)).body("role", equalTo("CUSTOMER"))
                .extract().path("token");
        long product = Fixtures.product("8.00", 5);
        new CartClient().add(token, product, 2).then().statusCode(201);
        long order = new OrderClient().place(token).then().statusCode(201).extract().jsonPath().getLong("id");
        new OrderClient().pay(token, order, "4242424242424242").then().statusCode(200).body("status", equalTo("PAID"));

        auth.guest(email).then().statusCode(409).body("message", containsString("Forgot password"));
        auth.login(email, TestData.PASSWORD).then().statusCode(401);
    }

    @Test
    @DisplayName("A guest who sets a password with Forgot password has a full account with the same orders")
    void guestClaimsTheAccount() {
        MailpitClient mailpit = new MailpitClient();
        assumeTrue(mailpit.isAvailable(), "Mailpit is not running (-DmailpitUrl)");
        String email = TestData.uniqueEmail();
        String token = auth.guest(email).then().statusCode(201).extract().path("token");
        long product = Fixtures.product("8.00", 5);
        new CartClient().add(token, product, 1).then().statusCode(201);
        long order = new OrderClient().place(token).then().statusCode(201).extract().jsonPath().getLong("id");

        auth.forgotPassword(email).then().statusCode(202);
        auth.resetPassword(mailpit.resetToken(email, 1), "MyOwnPass1!").then().statusCode(204);
        String full = auth.login(email, "MyOwnPass1!").then().statusCode(200).body("guest", equalTo(false)).extract().path("token");
        new OrderClient().get(full, order).then().statusCode(200);
    }

    @Test
    @DisplayName("An e-mail with an account cannot check out as a guest; a broken e-mail is 400")
    void guestRules() {
        Customer c = Fixtures.newCustomer();
        auth.guest(c.email()).then().statusCode(409).body("message", equalTo("This e-mail has an account. Please log in."));
        auth.guest("not-an-email").then().statusCode(400).body("fieldErrors", hasKey("email"));
    }

    @Test
    @Tag("smoke")
    @DisplayName("Address book: first address is the default, makeDefault moves it, twins are 409, others' addresses are 404")
    void addressBook() {
        Customer ada = Fixtures.newCustomer();
        long home = addresses.add(ada.token(), AddressClient.body("Home", "1 Main St", false)).then().statusCode(201)
                .body("isDefault", equalTo(true)).extract().jsonPath().getLong("id");
        long office = addresses.add(ada.token(), AddressClient.body("Office", "9 Work Rd", false)).then().statusCode(201)
                .body("isDefault", equalTo(false)).extract().jsonPath().getLong("id");
        addresses.makeDefault(ada.token(), office).then().statusCode(200).body("isDefault", equalTo(true));
        addresses.list(ada.token()).then().statusCode(200).body("$", hasSize(2)).body("[0].id", equalTo((int) office));

        addresses.add(ada.token(), AddressClient.body(null, "1 main st", false)).then().statusCode(409)
                .body("message", containsString("already in your address book"));
        addresses.add(ada.token(), java.util.Map.of("address", java.util.Map.of("fullName", "", "line1", "x", "city", "y", "state", "ZZ", "zip", "1")))
                .then().statusCode(400).body("fieldErrors", hasKey("address.state")).body("fieldErrors", hasKey("address.zip"));

        Customer bob = Fixtures.newCustomer();
        addresses.delete(bob.token(), home).then().statusCode(404);
        addresses.delete(ada.token(), office).then().statusCode(204);
        addresses.list(ada.token()).then().body("[0].id", equalTo((int) home)).body("[0].isDefault", equalTo(true));
    }
}
