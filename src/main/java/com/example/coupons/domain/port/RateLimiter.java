package com.example.coupons.domain.port;

import java.time.Duration;

public interface RateLimiter {

    Decision tryConsume(String key);

    record Decision(boolean allowed, long remaining, Duration retryAfter) {
        public static Decision allowed(long remaining) {
            return new Decision(true, remaining, Duration.ZERO);
        }
        public static Decision denied(Duration retryAfter) {
            return new Decision(false, 0, retryAfter);
        }
    }
}
