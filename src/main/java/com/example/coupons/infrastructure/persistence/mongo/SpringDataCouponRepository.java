package com.example.coupons.infrastructure.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface SpringDataCouponRepository extends MongoRepository<CouponDocument, String> {

    Optional<CouponDocument> findByCodeLower(String codeLower);
}
