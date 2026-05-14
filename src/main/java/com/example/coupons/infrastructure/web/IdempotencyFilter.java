package com.example.coupons.infrastructure.web;

import com.example.coupons.domain.port.IdempotencyStore;
import com.example.coupons.infrastructure.config.CouponsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Idempotency wrapper for redemption requests. Implements the well-known
 * "Idempotency-Key" header pattern: the first request executes normally and
 * its response (status + body) is stored keyed by the header value; subsequent
 * requests with the same key get a byte-perfect replay - including the same
 * 4xx domain rejection - so callers may safely retry on network failures.
 */
@Component
class IdempotencyFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final Pattern REDEEM_PATH = Pattern.compile("^/api/v1/coupons/[^/]+/redeem$");

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;
    private final String headerName;

    IdempotencyFilter(IdempotencyStore store, ObjectMapper objectMapper, CouponsProperties properties) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.headerName = properties.idempotency().headerName();
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod()) || !REDEEM_PATH.matcher(request.getRequestURI()).matches()) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(headerName);
        if (key == null || key.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        if (key.length() > 200 || !key.matches("[A-Za-z0-9._:-]+")) {
            log.info("Rejecting malformed idempotency key");
            writeProblem(response, HttpStatus.BAD_REQUEST, "invalid-idempotency-key",
                    "Idempotency-Key must be 1..200 chars of [A-Za-z0-9._:-]");
            return;
        }

        CachingRequest cachedRequest = new CachingRequest(request);
        String fingerprint = fingerprint(request, cachedRequest.body());

        Optional<IdempotencyStore.StoredResponse> existing = store.reserve(key, fingerprint);
        if (existing.isPresent()) {
            IdempotencyStore.StoredResponse stored = existing.get();
            if (!stored.requestFingerprint().equals(fingerprint)) {
                log.warn("Idempotency-Key reuse with different payload key={}", key);
                writeProblem(response, HttpStatus.UNPROCESSABLE_ENTITY, "idempotency-key-conflict",
                        "Idempotency-Key has been used with a different request body");
                return;
            }
            log.info("Replaying cached response for idempotency key={}", key);
            response.setStatus(stored.status());
            if (stored.contentType() != null) {
                response.setContentType(stored.contentType());
            }
            response.setHeader("Idempotent-Replay", "true");
            response.getOutputStream().write(stored.body());
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(cachedRequest, responseWrapper);
        } finally {
            byte[] body = responseWrapper.getContentAsByteArray();
            store.complete(key, new IdempotencyStore.StoredResponse(
                    responseWrapper.getStatus(),
                    responseWrapper.getContentType(),
                    body,
                    fingerprint));
            responseWrapper.copyBodyToResponse();
        }
    }

    private String fingerprint(HttpServletRequest request, byte[] body) {
        String input = request.getMethod() + ":" + request.getRequestURI() + ":"
                + new String(body, StandardCharsets.UTF_8);
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create("https://api.coupons.example.com/problems/" + code));
        body.setProperty("errorCode", code);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /**
     * Wraps the request so the body can be read for fingerprinting and then
     * re-read by Spring's message converters via {@link CachingRequest#getInputStream()}.
     */
    private static final class CachingRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] body;

        CachingRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = StreamUtils.copyToByteArray(request.getInputStream());
        }

        byte[] body() { return body; }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            java.io.ByteArrayInputStream src = new java.io.ByteArrayInputStream(body);
            return new jakarta.servlet.ServletInputStream() {
                @Override public boolean isFinished() { return src.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(jakarta.servlet.ReadListener readListener) {}
                @Override public int read() { return src.read(); }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    @SuppressWarnings("unused")
    private static String generateKey() {
        return UUID.randomUUID().toString();
    }
}
