package com.hris.identity.security;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP token-bucket rate limiting on the credential-bearing endpoints
 * (hardening, design step 7). Sits in front of the security filter chains so
 * rejected requests never reach authentication.
 *
 * <p>Limits are deliberately generous for legitimate use — the goal is to slow
 * online password guessing and reset-email flooding, not to throttle users.
 * Behind a reverse proxy, derive the client IP from X-Forwarded-For instead
 * of the socket address.
 */
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private record Rule(String path, int capacity, Duration window) {}

    private static final Rule[] RULES = {
        new Rule("/login",                    10, Duration.ofMinutes(1)),
        new Rule("/oauth2/token",             30, Duration.ofMinutes(1)),
        new Rule("/api/auth/forgot-password",  5, Duration.ofMinutes(15)),
        new Rule("/api/auth/reset-password",  10, Duration.ofMinutes(15)),
        new Rule("/api/auth/activate",        10, Duration.ofMinutes(15)),
        new Rule("/api/auth/change-password", 10, Duration.ofMinutes(15)),
    };

    /** Safety valve: a scan from many spoofed IPs cannot grow the map unboundedly. */
    private static final int MAX_TRACKED_BUCKETS = 50_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Rule rule = matchRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (buckets.size() > MAX_TRACKED_BUCKETS) {
            buckets.clear();
        }

        String key = rule.path() + '|' + request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> Bucket.builder()
            .addLimit(limit -> limit.capacity(rule.capacity())
                .refillGreedy(rule.capacity(), rule.window()))
            .build());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit exceeded on {} from {}", rule.path(), request.getRemoteAddr());
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(rule.window().toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests. Try again later.\"}");
    }

    private Rule matchRule(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        for (Rule rule : RULES) {
            if (rule.path().equals(path)) {
                return rule;
            }
        }
        return null;
    }
}
