package com.example.coupons.domain.exception;

import com.example.coupons.domain.model.CouponCode;

public class CouponNotFoundException extends CouponDomainException {
    public CouponNotFoundException(CouponCode code) {
        super("coupon-not-found", "Coupon '" + code + "' does not exist");
    }
}
