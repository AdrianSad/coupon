package com.example.coupons.infrastructure.web.dto;

import com.example.coupons.application.RedeemCouponUseCase;
import com.example.coupons.domain.model.Redemption;

import java.time.Instant;

public record RedemptionResponse(
        String couponCode,
        String userId,
        String country,
        Instant redeemedAt,
        int remainingUses
) {
    public static RedemptionResponse from(RedeemCouponUseCase.Result result) {
        Redemption r = result.redemption();
        return new RedemptionResponse(
                r.couponCode().raw(),
                r.userId().value(),
                r.country().value(),
                r.redeemedAt(),
                result.remainingUses());
    }
}
