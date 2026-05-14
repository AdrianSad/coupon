package com.example.coupons.domain.model;

import java.util.Objects;

public record UserId(String value) {

    private static final int MAX_LENGTH = 128;

    public UserId {
        Objects.requireNonNull(value, "userId");
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("userId must be 1.." + MAX_LENGTH + " chars");
        }
    }

    public static UserId of(String raw) {
        Objects.requireNonNull(raw, "userId");
        return new UserId(raw.trim());
    }

    @Override
    public String toString() {
        return value;
    }
}
