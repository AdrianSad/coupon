package com.example.coupons.infrastructure.ratelimit;

import com.example.coupons.domain.port.RateLimiter;
import com.example.coupons.infrastructure.config.CouponsProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class RedisBucket4jRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisBucket4jRateLimiter.class);

    private final ProxyManager<String> proxyManager;
    private final Supplier<BucketConfiguration> redeemConfig;

    public RedisBucket4jRateLimiter(ProxyManager<String> proxyManager, CouponsProperties properties) {
        this.proxyManager = proxyManager;
        CouponsProperties.RateLimit.Bucket cfg = properties.rateLimit().redeem();
        this.redeemConfig = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(cfg.capacity())
                        .refillGreedy(cfg.refillTokens(), Duration.ofSeconds(cfg.refillPeriodSeconds()))
                        .build())
                .build();
    }

    @Override
    public Decision tryConsume(String key) {
        try {
            BucketProxy bucket = proxyManager.builder().build(key, redeemConfig);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                return Decision.allowed(probe.getRemainingTokens());
            }
            return Decision.denied(Duration.ofNanos(probe.getNanosToWaitForRefill()));
        } catch (RuntimeException e) {
            // Fail-open: prefer availability over a hard outage when Redis flaps.
            // Operators see the error, while requests keep flowing.
            log.error("Rate limiter backend unavailable for key {} - failing open", key, e);
            return Decision.allowed(Long.MAX_VALUE);
        }
    }
}

@Configuration
class RateLimiterBackend {

    /**
     * Single Lettuce client that respects host / port / password / SSL or a
     * full redis URL. Hosted Redis providers (Upstash, Redis Cloud, etc.) all
     * require AUTH + TLS, so we need more than the bare host+port pair.
     * {@code spring.data.redis.url} wins if set (single source of truth for
     * "rediss://default:pwd@host:port" connection strings).
     */
    @Bean(destroyMethod = "shutdown")
    RedisClient lettuceRedisClient(
            @Value("${spring.data.redis.url:}") String url,
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean ssl) {

        if (url != null && !url.isBlank()) {
            return RedisClient.create(RedisURI.create(url));
        }
        RedisURI.Builder b = RedisURI.builder().withHost(host).withPort(port).withSsl(ssl);
        if (password != null && !password.isBlank()) {
            b.withPassword(password.toCharArray());
        }
        return RedisClient.create(b.build());
    }

    @Bean(destroyMethod = "close")
    StatefulRedisConnection<String, byte[]> bucketConnection(RedisClient client) {
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return client.connect(codec);
    }

    @Bean
    ProxyManager<String> bucketProxyManager(StatefulRedisConnection<String, byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }
}
