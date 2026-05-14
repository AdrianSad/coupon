package com.example.coupons.domain.exception;

public abstract class CouponDomainException extends RuntimeException {

    private final String errorCode;

    protected CouponDomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
