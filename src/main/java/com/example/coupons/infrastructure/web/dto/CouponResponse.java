package com.example.coupons.infrastructure.web.dto;

import com.example.coupons.domain.model.Coupon;

import java.time.Instant;

public record CouponResponse(
        String code,
        String country,
        int maxUses,
        int currentUses,
        Instant createdAt
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.code().raw(),
                coupon.country().value(),
                coupon.maxUses(),
                coupon.currentUses(),
                coupon.createdAt());
    }
}
