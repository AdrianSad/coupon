package com.example.coupons.infrastructure.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataRedemptionRepository extends MongoRepository<RedemptionDocument, String> {

    boolean existsByCouponCodeAndUserId(String couponCode, String userId);
}
