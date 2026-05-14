package com.example.coupons.infrastructure.persistence.mongo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.util.concurrent.TimeUnit;

@Configuration
class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    private final MongoTemplate mongoTemplate;

    MongoConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Bean
    MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    /**
     * Indexes are created explicitly (auto-index-creation is off in production)
     * so each index build produces a clear log line and can be replaced by a
     * dedicated migration job for hot collections.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        mongoTemplate.indexOps(CouponDocument.class).ensureIndex(
                new Index().on("codeLower", Sort.Direction.ASC).unique().named("uniq_codeLower"));

        mongoTemplate.indexOps(RedemptionDocument.class).ensureIndex(
                new Index()
                        .on("couponCode", Sort.Direction.ASC)
                        .on("userId", Sort.Direction.ASC)
                        .unique()
                        .named("uniq_coupon_user"));
        mongoTemplate.indexOps(RedemptionDocument.class).ensureIndex(
                new Index().on("couponCode", Sort.Direction.ASC).named("by_coupon"));

        mongoTemplate.indexOps(IdempotencyDocument.class).ensureIndex(
                new Index().on("expiresAt", Sort.Direction.ASC).expire(0, TimeUnit.SECONDS).named("ttl_expiresAt"));

        log.info("Mongo indexes ensured: coupons.uniq_codeLower, redemptions.uniq_coupon_user, "
                + "redemptions.by_coupon, idempotency_keys.ttl_expiresAt");
    }
}
