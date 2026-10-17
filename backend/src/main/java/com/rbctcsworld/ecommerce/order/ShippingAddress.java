package com.rbctcsworld.ecommerce.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Where an order is delivered. US addresses only (the shop ships inside the United States).
 * Validation (400 with the field name):
 *   fullName 1-80, line1 1-100, line2 0-100, city 1-60
 *   state    a US state or DC as its 2-letter code, any case ("pa" is stored as "PA")
 *   zip      12345 or 12345-6789
 *   phone    optional, 7-20 of: digits + ( ) - . space
 * Values are trimmed before they are stored; an empty line2 or phone is stored as "not given".
 */
public record ShippingAddress(
        @NotBlank @Size(max = 80) String fullName,
        @NotBlank @Size(max = 100) String line1,
        @Size(max = 100) String line2,
        @NotBlank @Size(max = 60) String city,
        @NotBlank @Pattern(regexp = STATES, message = "must be a US state code such as PA") String state,
        @NotBlank @Pattern(regexp = "^\\s*\\d{5}(-\\d{4})?\\s*$", message = "must be a US ZIP code: 12345 or 12345-6789") String zip,
        @Pattern(regexp = "^\\s*([0-9+().\\- ]{7,20})?\\s*$", message = "must be 7-20 digits or + ( ) - . characters") String phone) {

    static final String STATES = "(?i)^\\s*(AL|AK|AZ|AR|CA|CO|CT|DE|DC|FL|GA|HI|ID|IL|IN|IA|KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT"
            + "|NE|NV|NH|NJ|NM|NY|NC|ND|OH|OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY)\\s*$";

    /** Trimmed copy as it is stored: state in capitals, blank optional fields as null. */
    public ShippingAddress normalized() {
        return new ShippingAddress(fullName.trim(), line1.trim(), blankToNull(line2), city.trim(),
                state.trim().toUpperCase(java.util.Locale.ROOT), zip.trim(), blankToNull(phone));
    }

    /** "Ada Lovelace, 12 Main St, Apt 4, Pittsburgh, PA 15213" - one line for the order e-mail. */
    public String oneLine() {
        return fullName + ", " + line1 + (line2 == null ? "" : ", " + line2) + ", " + city + ", " + state + " " + zip;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
