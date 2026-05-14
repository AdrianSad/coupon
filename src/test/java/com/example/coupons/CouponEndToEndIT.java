package com.example.coupons;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CouponEndToEndIT extends IntegrationTestBase {

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired MongoTemplate mongo;

    @BeforeEach
    void cleanDb() {
        // Wipe documents but keep collections + indexes so the unique-index
        // and TTL guarantees stay in place between tests.
        for (String col : List.of("coupons", "redemptions", "idempotency_keys")) {
            mongo.getCollection(col).deleteMany(new org.bson.Document());
        }
    }

    @Test
    void should_create_coupon_and_redeem_it_end_to_end() throws Exception {
        createCoupon("WIOSNA", 5, "PL");

        ResponseEntity<String> response = redeem("wiosna", "user-1", null, "8.8.8.8");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.get("couponCode").asText()).isEqualTo("WIOSNA");
        assertThat(body.get("remainingUses").asInt()).isEqualTo(4);
    }

    @Test
    void should_return_404_problem_when_coupon_missing() throws Exception {
        ResponseEntity<String> response = redeem("missing", "u", null, "8.8.8.8");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(mapper.readTree(response.getBody()).get("errorCode").asText())
                .isEqualTo("coupon-not-found");
    }

    @Test
    void should_return_409_when_same_user_redeems_twice() throws Exception {
        createCoupon("OWA", 5, "PL");
        redeem("OWA", "user-1", null, "8.8.8.8");

        ResponseEntity<String> second = redeem("OWA", "user-1", null, "8.8.8.8");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapper.readTree(second.getBody()).get("errorCode").asText())
                .isEqualTo("coupon-already-redeemed");
    }

    /**
     * Idempotent replay: same key + same body must reuse the very first
     * response - we expect identical status, identical payload, and the
     * X-Idempotent-Replay marker header set to true on the second call.
     */
    @Test
    void should_replay_response_when_idempotency_key_repeats() throws Exception {
        createCoupon("IDM", 5, "PL");
        String key = UUID.randomUUID().toString();

        ResponseEntity<String> first = redeem("IDM", "user-9", key, "8.8.8.8");
        ResponseEntity<String> second = redeem("IDM", "user-9", key, "8.8.8.8");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(second.getHeaders().getFirst("Idempotent-Replay")).isEqualTo("true");
    }

    /**
     * Concurrency stress test - the central guarantee of the service.
     * We launch many parallel redemptions against a coupon with maxUses=K
     * and assert that exactly K calls succeed; the remaining ones receive
     * "coupon-exhausted". If the atomic findAndModify or the unique index
     * regresses, this test will produce more than K successes.
     */
    @Test
    void should_never_exceed_maxUses_under_parallel_load() throws Exception {
        int maxUses = 50;
        int attempts = 200;
        createCoupon("RACE", maxUses, "PL");

        ExecutorService pool = Executors.newFixedThreadPool(32);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger exhausted = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            String userId = "user-" + i;
            futures.add(pool.submit(() -> {
                try {
                    ResponseEntity<String> r = redeem("RACE", userId, null, "8.8.8.8");
                    if (r.getStatusCode().is2xxSuccessful()) {
                        ok.incrementAndGet();
                    } else if (r.getStatusCode() == HttpStatus.CONFLICT) {
                        exhausted.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                    }
                } catch (Exception e) {
                    other.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(ok.get()).as("successful redemptions").isEqualTo(maxUses);
        assertThat(other.get()).as("non-2xx non-409 responses").isZero();
        assertThat(exhausted.get()).isEqualTo(attempts - maxUses);
        long couponUses = mongo.getCollection("coupons").find().first().getInteger("currentUses");
        assertThat(couponUses).isEqualTo(maxUses);
        long redemptionDocs = mongo.getCollection("redemptions").countDocuments();
        assertThat(redemptionDocs).isEqualTo(maxUses);
    }

    private void createCoupon(String code, int maxUses, String country) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":\"" + code + "\",\"maxUses\":" + maxUses + ",\"country\":\"" + country + "\"}";
        ResponseEntity<String> response = http.exchange("/api/v1/coupons", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> redeem(String code, String userId, String idempotencyKey, String ip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Forwarded-For", ip);
        if (idempotencyKey != null) {
            headers.add("Idempotency-Key", idempotencyKey);
        }
        String body = "{\"userId\":\"" + userId + "\"}";
        return http.exchange("/api/v1/coupons/" + code + "/redeem", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }
}
