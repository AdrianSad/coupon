package com.example.coupons.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Value object representing a coupon code.
 * Equality is case-insensitive: WIOSNA and wiosna are the same code.
 */
public final class CouponCode {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 64;

    private final String raw;
    private final String normalized;

    private CouponCode(String raw, String normalized) {
        this.raw = raw;
        this.normalized = normalized;
    }

    public static CouponCode of(String raw) {
        Objects.requireNonNull(raw, "code");
        String trimmed = raw.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "coupon code must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        return new CouponCode(trimmed, trimmed.toLowerCase(Locale.ROOT));
    }

    public String raw() {
        return raw;
    }

    public String normalized() {
        return normalized;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CouponCode other && normalized.equals(other.normalized);
    }

    @Override
    public int hashCode() {
        return normalized.hashCode();
    }

    @Override
    public String toString() {
        return raw;
    }
}
