package com.example.coupons.infrastructure.persistence.mongo;

import com.example.coupons.domain.exception.DuplicateCouponCodeException;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.port.CouponRepository;
import com.mongodb.DuplicateKeyException;
import org.bson.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class MongoCouponRepository implements CouponRepository {

    private final SpringDataCouponRepository delegate;
    private final MongoTemplate mongoTemplate;

    MongoCouponRepository(SpringDataCouponRepository delegate, MongoTemplate mongoTemplate) {
        this.delegate = delegate;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Coupon save(Coupon coupon) {
        try {
            return delegate.save(CouponDocument.from(coupon)).toDomain();
        } catch (DuplicateKeyException | DataIntegrityViolationException e) {
            throw new DuplicateCouponCodeException(coupon.code());
        }
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return delegate.findByCodeLower(code.normalized()).map(CouponDocument::toDomain);
    }

    /**
     * Single-document atomic operation: matches only when there is capacity left
     * ({@code currentUses < maxUses}) and increments the counter in the same
     * round-trip. This is what makes "first come first served" hold across pods.
     */
    @Override
    public Optional<Coupon> incrementUsageIfAvailable(CouponCode code) {
        Query query = new BasicQuery(new Document()
                .append("codeLower", code.normalized())
                .append("$expr", new Document("$lt", List.of("$currentUses", "$maxUses"))));

        Update update = new Update().inc("currentUses", 1);

        CouponDocument updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                CouponDocument.class);

        return Optional.ofNullable(updated).map(CouponDocument::toDomain);
    }

    @Override
    public void decrementUsage(CouponCode code) {
        Query query = new Query(Criteria.where("codeLower").is(code.normalized())
                .and("currentUses").gt(0));
        mongoTemplate.findAndModify(
                query,
                new Update().inc("currentUses", -1),
                FindAndModifyOptions.options().returnNew(true),
                CouponDocument.class);
    }
}
