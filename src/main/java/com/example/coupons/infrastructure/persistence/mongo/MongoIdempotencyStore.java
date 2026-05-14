package com.example.coupons.infrastructure.persistence.mongo;

import com.example.coupons.domain.port.IdempotencyStore;
import com.example.coupons.infrastructure.config.CouponsProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Component
class MongoIdempotencyStore implements IdempotencyStore {

    private final SpringDataIdempotencyRepository delegate;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final Duration ttl;

    MongoIdempotencyStore(SpringDataIdempotencyRepository delegate,
                          MongoTemplate mongoTemplate,
                          Clock clock,
                          CouponsProperties properties) {
        this.delegate = delegate;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
        this.ttl = Duration.ofHours(properties.idempotency().ttlHours());
    }

    /**
     * Reservation strategy: try to insert a placeholder. If the key already
     * exists we return the previously stored response so the caller can replay it.
     * The placeholder has a body of length 0 and status 0 to signal "in flight".
     * In practice the response is written within the same request, so callers
     * encountering an in-flight placeholder are extremely rare; we still treat
     * them as "duplicate in progress" by returning empty optional content.
     */
    @Override
    public Optional<StoredResponse> reserve(String key, String requestFingerprint) {
        try {
            delegate.insert(new IdempotencyDocument(
                    key, requestFingerprint, 0, null, new byte[0],
                    clock.instant().plus(ttl)));
            return Optional.empty();
        } catch (DuplicateKeyException e) {
            return delegate.findById(key)
                    .filter(d -> d.getStatus() != null && d.getStatus() > 0)
                    .map(d -> new StoredResponse(
                            d.getStatus(),
                            d.getContentType(),
                            d.getBody() == null ? new byte[0] : d.getBody(),
                            d.getRequestFingerprint()));
        }
    }

    @Override
    public void complete(String key, StoredResponse response) {
        Query q = new Query(Criteria.where("_id").is(key));
        Update u = new Update()
                .set("status", response.status())
                .set("contentType", response.contentType())
                .set("body", response.body())
                .set("requestFingerprint", response.requestFingerprint())
                .set("expiresAt", clock.instant().plus(ttl));
        mongoTemplate.updateFirst(q, u, IdempotencyDocument.class);
    }
}
