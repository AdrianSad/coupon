package com.example.coupons.domain.port;

import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CouponCode;

import java.util.Optional;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findByCode(CouponCode code);

    /**
     * Atomically increments {@code currentUses} iff the coupon exists and
     * {@code currentUses < maxUses}. Returns the updated aggregate, or empty
     * if no document matched (i.e. exhausted or missing). This is the linchpin
     * of the "first come first served" guarantee under multi-pod load.
     */
    Optional<Coupon> incrementUsageIfAvailable(CouponCode code);

    /**
     * Compensating decrement used to roll back a usage when the post-increment
     * step (e.g. inserting the per-user redemption) fails outside a Mongo
     * transaction. Inside a transaction we rely on transactional rollback.
     */
    void decrementUsage(CouponCode code);
}
