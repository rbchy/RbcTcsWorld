package com.rbctcsworld.ecommerce.common.exception;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.assertj.core.api.Assertions.assertThat;

/** The fallback handler: client mistakes keep a 4xx status, only real server faults become 500. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");

    @Test
    void malformedQueryStringFromTomcatIs400NotA500_DEF017() {
        ResponseEntity<ApiError> r = handler.unexpected(new InvalidParameterException("empty parameter name", 400), req);
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody().message()).isEqualTo("Malformed query string or form parameters");
    }

    @Test
    void wrappedOrCodelessTomcatParameterErrorIsStillAClientError() {
        assertThat(handler.unexpected(new IllegalStateException("wrapper",
                new InvalidParameterException("too many parameters", 413)), req).getStatusCode().value()).isEqualTo(413);
        assertThat(handler.unexpected(new InvalidParameterException("no code"), req).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void springMvcExceptionsKeepTheirStatus() {
        assertThat(handler.unexpected(new HttpRequestMethodNotSupportedException("PATCH"), req).getStatusCode().value())
                .isEqualTo(405);
    }

    @Test
    void anythingElseIsA500WithoutDetails() {
        ResponseEntity<ApiError> r = handler.unexpected(new IllegalStateException("database password is x"), req);
        assertThat(r.getStatusCode().value()).isEqualTo(500);
        assertThat(r.getBody().message()).isEqualTo("Unexpected server error").doesNotContain("password");
    }

    /** REQ-CAT-15: a photo above the multipart limit is 413 with the same words as PictureProcessor (ADR-0017). */
    @Test
    void aPhotoAboveTheUploadLimitIs413() {
        ResponseEntity<ApiError> r = handler.tooLarge(
                new org.springframework.web.multipart.MaxUploadSizeExceededException(10L * 1024 * 1024), req);
        assertThat(r.getStatusCode().value()).isEqualTo(413);
        assertThat(r.getBody().message()).isEqualTo("The picture is larger than 10 MB");
    }
}
