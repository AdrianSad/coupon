package com.example.coupons.infrastructure.geo;

import com.example.coupons.domain.exception.GeolocationUnavailableException;
import com.example.coupons.domain.model.CountryCode;
import com.example.coupons.domain.port.IpGeolocationService;
import com.example.coupons.infrastructure.config.CouponsProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

@Component
class IpApiClient implements IpGeolocationService {

    private static final Logger log = LoggerFactory.getLogger(IpApiClient.class);

    private final RestClient restClient;
    private final Cache<String, CountryCode> cache;
    private final CouponsProperties.Geolocation properties;

    IpApiClient(RestClient.Builder builder, CouponsProperties properties) {
        this.properties = properties.geolocation();
        this.restClient = builder.baseUrl(this.properties.baseUrl()).build();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(this.properties.cacheTtlMinutes()))
                .maximumSize(50_000)
                .build();
    }

    @Override
    public CountryCode countryFor(String ip) {
        if (properties.skipPrivateIps() && isLocalOrPrivate(ip) && properties.fallbackCountry() != null
                && !properties.fallbackCountry().isBlank()) {
            return CountryCode.of(properties.fallbackCountry());
        }
        CountryCode cached = cache.getIfPresent(ip);
        if (cached != null) {
            return cached;
        }
        CountryCode resolved = lookup(ip);
        cache.put(ip, resolved);
        return resolved;
    }

    @Retry(name = "ipApi")
    @CircuitBreaker(name = "ipApi", fallbackMethod = "lookupFallback")
    CountryCode lookup(String ip) {
        try {
            IpApiResponse response = restClient.get()
                    .uri(uri -> uri.path("/json/{ip}").queryParam("fields", "status,countryCode,message").build(ip))
                    .retrieve()
                    .body(IpApiResponse.class);

            if (response == null || !"success".equalsIgnoreCase(response.status())
                    || response.countryCode() == null) {
                log.warn("ip-api returned non-success for {}: {}", ip, response);
                throw new GeolocationUnavailableException("ip-api returned no country for " + ip);
            }
            return CountryCode.of(response.countryCode());
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("ip-api rate-limited the service");
            throw new GeolocationUnavailableException("ip-api rate-limited");
        } catch (RestClientException e) {
            log.warn("ip-api transport failure for {}: {}", ip, e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unused")
    CountryCode lookupFallback(String ip, Throwable t) {
        log.error("Geolocation fallback engaged for {} after {}", ip, t.toString());
        if (properties.fallbackCountry() != null && !properties.fallbackCountry().isBlank()) {
            return CountryCode.of(properties.fallbackCountry());
        }
        throw new GeolocationUnavailableException("geolocation provider unavailable");
    }

    private boolean isLocalOrPrivate(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
