package com.example.coupons.infrastructure.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataIdempotencyRepository extends MongoRepository<IdempotencyDocument, String> {
}
