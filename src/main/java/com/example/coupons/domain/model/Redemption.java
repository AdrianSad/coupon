package com.example.coupons.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Redemption(
        String id,
        CouponCode couponCode,
        UserId userId,
        CountryCode country,
        Instant redeemedAt
) {
    public Redemption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(couponCode, "couponCode");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(redeemedAt, "redeemedAt");
    }
}
