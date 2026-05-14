package com.example.coupons.domain.exception;

import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.UserId;

public class CouponAlreadyRedeemedException extends CouponDomainException {
    public CouponAlreadyRedeemedException(CouponCode code, UserId userId) {
        super("coupon-already-redeemed",
                "User '" + userId + "' has already redeemed coupon '" + code + "'");
    }
}
