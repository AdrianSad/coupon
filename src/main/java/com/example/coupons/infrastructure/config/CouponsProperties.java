package com.example.coupons.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

import java.util.List;

@ConfigurationProperties(prefix = "coupons")
public record CouponsProperties(
        Geolocation geolocation,
        RateLimit rateLimit,
        Idempotency idempotency,
        Cors cors
) {

    public record Geolocation(
            String baseUrl,
            int cacheTtlMinutes,
            boolean skipPrivateIps,
            @Nullable String fallbackCountry
    ) {}

    public record RateLimit(Bucket redeem) {
        public record Bucket(long capacity, long refillTokens, long refillPeriodSeconds) {}
    }

    public record Idempotency(int ttlHours, String headerName) {}

    public record Cors(List<String> allowedOrigins) {}
}
