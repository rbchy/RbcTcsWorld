package com.rbctcsworld.ecommerce.qa.tests.security;

import com.rbctcsworld.ecommerce.qa.api.RawClient;
import com.rbctcsworld.ecommerce.qa.testdata.Fixtures;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OWASP A01 Broken Access Control: one table = the whole authorization model.
 * Every row is called three times: anonymous, customer, admin. Reading the table is reviewing the design.
 */
@Tag("security")
@Epic("Security")
@Feature("Access control matrix")
class AccessControlMatrixTest {

    private static String customer;
    private static String admin;
    private static long productId;
    private final RawClient raw = new RawClient();

    @BeforeAll
    static void tokens() {
        customer = Fixtures.newCustomer().token();
        admin = Fixtures.adminToken();
        productId = Fixtures.product("5.00", 5);
    }

    @ParameterizedTest(name = "{0} {1}: anonymous {2}, customer {3}, admin {4}")
    @CsvSource({
            // method, path,                               anonymous, customer, admin
            "GET,    /api/products,                         200, 200, 200",
            "GET,    /api/products/{pid}/reviews,             200, 200, 200",
            "GET,    /api/tracking/UPS-000000000000,        404, 404, 404",
            "GET,    /api/store/legal,                      200, 200, 200",
            // contact form (ADR-0018): anybody may write (an empty form is 400 for everyone); only the admin reads
            "GET,    /api/contact,                          200, 200, 200",
            "POST,   /api/contact,                          400, 400, 400",
            "GET,    /api/admin/messages,                   401, 403, 200",
            "POST,   /api/admin/messages/99999999/close,    401, 403, 404",
            "POST,   /api/products,                         401, 403, 400",
            "PUT,    /api/products/{pid},                     401, 403, 400",
            "DELETE, /api/products/99999999,                401, 403, 404",
            // product photos (ADR-0017): only an admin uploads, and only as multipart (a JSON body is 415); pictures are public
            "POST,   /api/products/pictures,                401, 403, 415",
            "GET,    /api/products/pictures/0000000000000000000000000000000000000000000000000000000000000000.jpg, 404, 404, 404",
            "GET,    /api/cart,                             401, 200, 200",
            "GET,    /api/orders,                           401, 200, 200",
            "GET,    /api/wishlist,                         401, 200, 200",
            "GET,    /api/reviews/mine,                     401, 200, 200",
            "POST,   /api/checkout/quote,                   401, 400, 400",
            "GET,    /api/payments/config,                  401, 200, 200",
            "POST,   /api/orders/99999999/card-payment,     401, 404, 404",
            // Stripe has no login: it proves itself with a signature, so everyone without one gets 400
            "POST,   /api/payments/stripe/webhook,          400, 400, 400",
            // Shippo has no login either: its webhook URL carries a secret token, so a call without one is 400
            "POST,   /api/shipping/shippo/webhook,          400, 400, 400",
            // Amazon SNS proves itself with a signature; until a topic is configured nobody gets in (503)
            "POST,   /api/notifications/ses/events,         503, 503, 503",
            "GET,    /api/admin/orders/99999999/label,      401, 403, 404",
            "GET,    /api/admin/orders,                     401, 403, 200",
            "GET,    /api/admin/returns,                    401, 403, 200",
            "GET,    /api/admin/reviews,                    401, 403, 200",
            "POST,   /api/admin/reviews/99999999/hide,      401, 403, 404",
            "POST,   /api/admin/orders/99999999/deliver,    401, 403, 404",
            "GET,    /actuator/health,                      200, 200, 200",
            "GET,    /actuator/env,                         401, 404, 404",
            "GET,    /actuator/heapdump,                    401, 404, 404",
    })
    @DisplayName("Who may call what")
    void matrix(String method, String path, int anonymous, int asCustomer, int asAdmin) {
        path = path.replace("{pid}", String.valueOf(productId));
        Object body = method.equals("POST") || method.equals("PUT") ? "{}" : null;
        int a = raw.send(method, path, null, body).statusCode();
        int c = raw.send(method, path, customer, body).statusCode();
        int d = raw.send(method, path, admin, body).statusCode();
        assertAll(
                () -> assertEquals(anonymous, a, "anonymous"),
                () -> assertEquals(asCustomer, c, "customer"),
                () -> assertEquals(asAdmin, d, "admin"));
    }
}
