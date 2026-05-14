package com.example.coupons.infrastructure.web;

import com.example.coupons.domain.port.RateLimiter;
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
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * Per-IP rate limiter for the redeem endpoint. Runs ahead of the main handler
 * but after the request dispatcher so we can match on the path. Order is set
 * so this filter executes before the IdempotencyFilter; rejecting at the gate
 * means a 429 will not occupy an idempotency key slot.
 */
@Component
class RateLimitFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Pattern REDEEM_PATH = Pattern.compile("^/api/v1/coupons/[^/]+/redeem$");

    private final RateLimiter rateLimiter;
    private final ClientIpResolver ipResolver;
    private final ObjectMapper objectMapper;

    RateLimitFilter(RateLimiter rateLimiter, ClientIpResolver ipResolver, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.ipResolver = ipResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod()) || !REDEEM_PATH.matcher(request.getRequestURI()).matches()) {
            chain.doFilter(request, response);
            return;
        }

        String ip = ipResolver.resolve(request);
        RateLimiter.Decision decision = rateLimiter.tryConsume("rl:redeem:" + ip);

        if (!decision.allowed()) {
            long retrySeconds = Math.max(1, decision.retryAfter().toSeconds());
            log.warn("Rate limit exceeded ip={} retryAfter={}s", ip, retrySeconds);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retrySeconds));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

            ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many redeem attempts from this IP");
            body.setType(URI.create("https://api.coupons.example.com/problems/rate-limited"));
            body.setProperty("errorCode", "rate-limited");
            body.setProperty("retryAfterSeconds", retrySeconds);
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }

        request.setAttribute(HandlerMapping.class.getName() + ".rateLimitRemaining", decision.remaining());
        chain.doFilter(request, response);
    }
}
