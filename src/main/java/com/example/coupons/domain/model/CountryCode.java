package com.example.coupons.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** ISO-3166-1 alpha-2 country code. */
public record CountryCode(String value) {

    private static final Pattern ISO_ALPHA2 = Pattern.compile("[A-Z]{2}");

    public CountryCode {
        Objects.requireNonNull(value, "country");
        if (!ISO_ALPHA2.matcher(value).matches()) {
            throw new IllegalArgumentException("country must be an ISO-3166-1 alpha-2 code");
        }
    }

    public static CountryCode of(String raw) {
        Objects.requireNonNull(raw, "country");
        return new CountryCode(raw.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
