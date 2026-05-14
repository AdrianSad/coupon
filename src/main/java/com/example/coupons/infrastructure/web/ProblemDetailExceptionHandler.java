package com.example.coupons.infrastructure.web;

import com.example.coupons.domain.exception.CountryNotAllowedException;
import com.example.coupons.domain.exception.CouponAlreadyRedeemedException;
import com.example.coupons.domain.exception.CouponDomainException;
import com.example.coupons.domain.exception.CouponExhaustedException;
import com.example.coupons.domain.exception.CouponNotFoundException;
import com.example.coupons.domain.exception.DuplicateCouponCodeException;
import com.example.coupons.domain.exception.GeolocationUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
class ProblemDetailExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);
    private static final String TYPE_BASE = "https://api.coupons.example.com/problems/";

    @ExceptionHandler(CouponNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(CouponNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(CouponExhaustedException.class)
    ResponseEntity<ProblemDetail> handleExhausted(CouponExhaustedException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(CouponAlreadyRedeemedException.class)
    ResponseEntity<ProblemDetail> handleAlreadyRedeemed(CouponAlreadyRedeemedException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(CountryNotAllowedException.class)
    ResponseEntity<ProblemDetail> handleCountry(CountryNotAllowedException e) {
        ProblemDetail body = base(HttpStatus.FORBIDDEN, e);
        body.setProperty("requiredCountry", e.required().value());
        body.setProperty("requesterCountry", e.actual().value());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(DuplicateCouponCodeException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(DuplicateCouponCodeException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(GeolocationUnavailableException.class)
    ResponseEntity<ProblemDetail> handleGeoDown(GeolocationUnavailableException e) {
        log.error("Geolocation provider failure: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleIllegalArg(IllegalArgumentException e) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        body.setType(URI.create(TYPE_BASE + "invalid-request"));
        body.setProperty("errorCode", "invalid-request");
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(err ->
                fields.put(err.getField(), err.getDefaultMessage()));
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        body.setType(URI.create(TYPE_BASE + "invalid-request"));
        body.setProperty("errorCode", "invalid-request");
        body.setProperty("violations", fields);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException e) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON request");
        body.setType(URI.create(TYPE_BASE + "invalid-request"));
        body.setProperty("errorCode", "invalid-request");
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled error processing {} {}", request.getMethod(), request.getRequestURI(), e);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
        body.setType(URI.create(TYPE_BASE + "internal-error"));
        body.setProperty("errorCode", "internal-error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, CouponDomainException e) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(base(status, e));
    }

    private ProblemDetail base(HttpStatusCode status, CouponDomainException e) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        body.setType(URI.create(TYPE_BASE + e.errorCode()));
        body.setProperty("errorCode", e.errorCode());
        return body;
    }
}
