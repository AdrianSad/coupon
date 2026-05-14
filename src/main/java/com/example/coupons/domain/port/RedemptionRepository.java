package com.example.coupons.domain.port;

import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.Redemption;
import com.example.coupons.domain.model.UserId;

public interface RedemptionRepository {

    /**
     * Persist a redemption. Implementations MUST translate a duplicate
     * {@code (couponCode, userId)} collision into
     * {@link com.example.coupons.domain.exception.CouponAlreadyRedeemedException}.
     */
    Redemption insert(Redemption redemption);

    boolean existsBy(CouponCode code, UserId userId);
}
