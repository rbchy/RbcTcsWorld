package com.rbctcsworld.ecommerce.order;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Rules of a delivery address: what is accepted, what is rejected, and how it is stored. */
class ShippingAddressTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void start() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stop() {
        factory.close();
    }

    private static ShippingAddress with(String state, String zip, String phone) {
        return new ShippingAddress("Ada Lovelace", "12 Main St", null, "Pittsburgh", state, zip, phone);
    }

    private static Set<String> invalidFields(ShippingAddress a) {
        return validator.validate(a).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void aCompleteAddressIsValid() {
        assertThat(invalidFields(with("PA", "15213", "412-555-0100"))).isEmpty();
        assertThat(invalidFields(with("pa", "15213-0001", null))).as("lower case state, ZIP+4, no phone").isEmpty();
        assertThat(invalidFields(with("DC", " 20001 ", ""))).as("DC, spaces around the ZIP, empty phone").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1521", "152130", "ABCDE", "15213-12", "15213 0001", ""})
    void aZipMustBeFiveDigitsOrZipPlusFour(String zip) {
        assertThat(invalidFields(with("PA", zip, null))).containsExactly("zip");
    }

    @ParameterizedTest
    @ValueSource(strings = {"XX", "P", "PAA", "Pennsylvania", "P A", ""})
    void theStateMustBeAUsStateCode(String state) {
        assertThat(invalidFields(with(state, "15213", null))).containsExactly("state");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "call me", "412-555-0100-412-555-0100", "<script>"})
    void aPhoneNumberThatIsGivenMustLookLikeOne(String phone) {
        assertThat(invalidFields(with("PA", "15213", phone))).containsExactly("phone");
    }

    @Test
    void requiredFieldsAndLengthLimits() {
        ShippingAddress blank = new ShippingAddress(" ", "", null, null, "PA", "15213", null);
        assertThat(invalidFields(blank)).containsExactlyInAnyOrder("fullName", "line1", "city");

        ShippingAddress tooLong = new ShippingAddress("n".repeat(81), "l".repeat(101), "x".repeat(101), "c".repeat(61),
                "PA", "15213", null);
        assertThat(invalidFields(tooLong)).containsExactlyInAnyOrder("fullName", "line1", "line2", "city");

        ShippingAddress longest = new ShippingAddress("n".repeat(80), "l".repeat(100), "x".repeat(100), "c".repeat(60),
                "PA", "15213", null);
        assertThat(invalidFields(longest)).as("exactly at the limits").isEmpty();
    }

    @Test
    void normalizedTrimsCapitalisesTheStateAndDropsEmptyOptionalFields() {
        ShippingAddress typed = new ShippingAddress(" Ada ", " 12 Main St ", "  ", " Pittsburgh ", " pa ", " 15213 ", " ");
        assertThat(typed.normalized())
                .isEqualTo(new ShippingAddress("Ada", "12 Main St", null, "Pittsburgh", "PA", "15213", null));

        ShippingAddress full = new ShippingAddress("Ada", "12 Main St", " Apt 4 ", "Pittsburgh", "PA", "15213", " 412-555-0100 ");
        assertThat(full.normalized().line2()).isEqualTo("Apt 4");
        assertThat(full.normalized().phone()).isEqualTo("412-555-0100");
        assertThat(with("PA", "15213", null).normalized().phone()).isNull();
    }

    @Test
    void oneLineForTheEmailWithAndWithoutASecondLine() {
        assertThat(new ShippingAddress("Ada", "12 Main St", "Apt 4", "Pittsburgh", "PA", "15213", "412").oneLine())
                .isEqualTo("Ada, 12 Main St, Apt 4, Pittsburgh, PA 15213");
        assertThat(new ShippingAddress("Ada", "12 Main St", null, "Pittsburgh", "PA", "15213", null).oneLine())
                .isEqualTo("Ada, 12 Main St, Pittsburgh, PA 15213");
    }

    @Test
    void anOrderFromBeforeAddressesHasNone() {
        CustomerOrder old = new CustomerOrder("ORD-1", 7L);
        assertThat(old.getShippingAddress()).isNull();

        old.shipTo(with("PA", "15213", null));
        assertThat(old.getShippingAddress()).isEqualTo(with("PA", "15213", null));
    }
}
