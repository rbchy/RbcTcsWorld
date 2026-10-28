package com.rbctcsworld.ecommerce.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts exceptions into correct HTTP status codes.
 * Before this class every failure became HTTP 500, which hid real defects from the tests.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ApiError> tooMany(TooManyRequestsException ex, HttpServletRequest req) {
        ResponseEntity<ApiError> r = build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), req);
        return ResponseEntity.status(r.getStatusCode())
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(r.getBody());
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiError> forbidden(ForbiddenException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> businessRule(BusinessRuleException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(PaymentUnavailableException.class)
    ResponseEntity<ApiError> paymentUnavailable(PaymentUnavailableException ex, HttpServletRequest req) {
        ResponseEntity<ApiError> r = build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), req);
        return ResponseEntity.status(r.getStatusCode()).header("Retry-After", "30").body(r.getBody());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ResponseEntity<ApiError> serviceUnavailable(ServiceUnavailableException ex, HttpServletRequest req) {
        ResponseEntity<ApiError> r = build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), req);
        return ResponseEntity.status(r.getStatusCode()).header("Retry-After", "30").body(r.getBody());
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    ResponseEntity<ApiError> paymentDeclined(PaymentDeclinedException ex, HttpServletRequest req) {
        return build(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> invalidCredentials(InvalidCredentialsException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        HttpStatus s = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(s)
                .body(ApiError.of(s.value(), s.getReasonPhrase(), "Validation failed", req.getRequestURI(), fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed or missing JSON request body", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Request conflicts with existing data", req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
        // DEF-017: Tomcat 11 (Spring Boot 4) rejects a malformed query string, e.g. "?=x" (a parameter without a
        // name), by throwing InvalidParameterException from request.getParameter(). It is the client's error.
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof InvalidParameterException ipe) {
                HttpStatus s = HttpStatus.resolve(ipe.getErrorCode());
                return build(s != null && s.is4xxClientError() ? s : HttpStatus.BAD_REQUEST,
                        "Malformed query string or form parameters", req);
            }
        }
        // Spring MVC's own exceptions (405 method not allowed, 404 no endpoint, 415 media type...)
        // already know their status code - keep it instead of turning them into 500.
        if (ex instanceof ErrorResponse er) {
            HttpStatus s = HttpStatus.resolve(er.getStatusCode().value());
            if (s == null) s = HttpStatus.BAD_REQUEST;
            return build(s, er.getBody().getDetail() != null ? er.getBody().getDetail() : s.getReasonPhrase(), req);
        }
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", req);
    }

    private ResponseEntity<ApiError> build(HttpStatus s, String message, HttpServletRequest req) {
        return ResponseEntity.status(s).body(ApiError.of(s.value(), s.getReasonPhrase(), message, req.getRequestURI()));
    }
}
