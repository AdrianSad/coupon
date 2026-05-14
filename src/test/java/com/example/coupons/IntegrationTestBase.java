package com.example.coupons;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));
    static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"));
    static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        MONGO.start();
        REDIS.start();
        WIREMOCK.start();
        WIREMOCK.stubFor(get(urlPathMatching("/json/.*")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"success\",\"countryCode\":\"PL\"}")));
    }

    @BeforeAll
    static void resetWiremock() {
        WIREMOCK.resetRequests();
    }

    @AfterAll
    static void stopAll() {
        // Containers are reused across tests via JVM shutdown hook.
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("coupons"));
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("coupons.geolocation.base-url", WIREMOCK::baseUrl);
        registry.add("coupons.geolocation.skip-private-ips", () -> "false");
        registry.add("coupons.rate-limit.redeem.capacity", () -> "10000");
        registry.add("coupons.rate-limit.redeem.refill-tokens", () -> "10000");
    }
}
