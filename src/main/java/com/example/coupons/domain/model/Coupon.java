package com.example.coupons.domain.model;

import com.example.coupons.domain.exception.CountryNotAllowedException;
import com.example.coupons.domain.exception.CouponExhaustedException;

import java.time.Instant;
import java.util.Objects;

/**
 * Coupon aggregate root. Encapsulates all redemption invariants so they can be
 * exercised in fast in-process unit tests independently of any infrastructure.
 *
 * <p>The actual concurrent enforcement of the usage cap happens in the persistence
 * adapter (atomic findAndModify); this aggregate is the source of truth for the
 * business rules and a fail-fast guard before we ever touch the database.</p>
 */
public record Coupon(
        String id,
        CouponCode code,
        Instant createdAt,
        int maxUses,
        int currentUses,
        CountryCode country,
        long version
) {

    public Coupon {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(country, "country");
        if (maxUses <= 0) {
            throw new IllegalArgumentException("maxUses must be positive");
        }
        if (currentUses < 0 || currentUses > maxUses) {
            throw new IllegalArgumentException("currentUses out of range");
        }
    }

    public static Coupon issue(String id, CouponCode code, int maxUses, CountryCode country, Instant now) {
        return new Coupon(id, code, now, maxUses, 0, country, 0L);
    }

    /**
     * Validate a redemption attempt. Throws a domain exception if the coupon
     * cannot be redeemed by this user from this country.
     *
     * <p>This does not mutate state - the actual increment happens atomically in
     * the persistence layer. We keep the rules here so they remain testable.</p>
     */
    public void assertRedeemableBy(CountryCode requesterCountry) {
        if (!country.equals(requesterCountry)) {
            throw new CountryNotAllowedException(code, country, requesterCountry);
        }
        if (currentUses >= maxUses) {
            throw new CouponExhaustedException(code);
        }
    }

    public boolean isExhausted() {
        return currentUses >= maxUses;
    }

    public int remainingUses() {
        return maxUses - currentUses;
    }
}
