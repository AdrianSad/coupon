package com.example.coupons.domain.exception;

import com.example.coupons.domain.model.CouponCode;

public class CouponExhaustedException extends CouponDomainException {
    public CouponExhaustedException(CouponCode code) {
        super("coupon-exhausted", "Coupon '" + code + "' has reached its maximum number of uses");
    }
}
