package com.example.coupons.infrastructure.persistence.mongo;

import com.example.coupons.domain.exception.CouponAlreadyRedeemedException;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.Redemption;
import com.example.coupons.domain.model.UserId;
import com.example.coupons.domain.port.RedemptionRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
class MongoRedemptionRepository implements RedemptionRepository {

    private final SpringDataRedemptionRepository delegate;

    MongoRedemptionRepository(SpringDataRedemptionRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Redemption insert(Redemption redemption) {
        try {
            delegate.insert(RedemptionDocument.from(redemption));
            return redemption;
        } catch (DuplicateKeyException e) {
            throw new CouponAlreadyRedeemedException(redemption.couponCode(), redemption.userId());
        }
    }

    @Override
    public boolean existsBy(CouponCode code, UserId userId) {
        return delegate.existsByCouponCodeAndUserId(code.normalized(), userId.value());
    }
}
