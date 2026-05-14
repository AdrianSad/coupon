package com.example.coupons.domain.exception;

import com.example.coupons.domain.model.CountryCode;
import com.example.coupons.domain.model.CouponCode;

public class CountryNotAllowedException extends CouponDomainException {

    private final CountryCode required;
    private final CountryCode actual;

    public CountryNotAllowedException(CouponCode code, CountryCode required, CountryCode actual) {
        super("country-not-allowed",
                "Coupon '" + code + "' is restricted to country " + required + " (request from " + actual + ")");
        this.required = required;
        this.actual = actual;
    }

    public CountryCode required() { return required; }
    public CountryCode actual() { return actual; }
}
