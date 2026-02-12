package com.example.jutjubic.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class RateLimitingFilter implements Filter {

    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final long WINDOW_MS = 60_000L;
    private static final String RATE_LIMITED_ENDPOINT = "/api/auth/login";
    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final String ERROR_RESPONSE_TEMPLATE = """
            {"error":"Too many requests - try again later","retryAfterSeconds":%d}""";

    private final Map<String, RateLimitAttempt> requestCounts = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        if (!shouldRateLimit(httpRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String clientIp = httpRequest.getRemoteAddr();

        if (isRateLimitExceeded(clientIp)) {
            sendRateLimitResponse(httpResponse, clientIp);
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private boolean shouldRateLimit(HttpServletRequest request) {
        return RATE_LIMITED_ENDPOINT.equals(request.getRequestURI());
    }

    private boolean isRateLimitExceeded(String clientIp) {
        long now = System.currentTimeMillis();
        RateLimitAttempt attempt = requestCounts.computeIfAbsent(clientIp, key -> new RateLimitAttempt());

        if (isWindowExpired(attempt, now)) {
            resetWindow(attempt, now);
        }

        int currentCount = attempt.count().incrementAndGet();
        return currentCount > MAX_REQUESTS_PER_MINUTE;
    }

    private boolean isWindowExpired(RateLimitAttempt attempt, long now) {
        return now - attempt.getWindowStart() > WINDOW_MS;
    }

    private void resetWindow(RateLimitAttempt attempt, long now) {
        attempt.count().set(0);
        attempt.setWindowStart(now);
    }

    private void sendRateLimitResponse(HttpServletResponse response, String clientIp) throws IOException {
        RateLimitAttempt attempt = requestCounts.get(clientIp);
        long retryAfterSeconds = calculateRetryAfterSeconds(attempt);

        log.debug("Rate limit exceeded for IP: {}, retry after: {}s", clientIp, retryAfterSeconds);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(RETRY_AFTER_HEADER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(ERROR_RESPONSE_TEMPLATE.formatted(retryAfterSeconds));
    }

    private long calculateRetryAfterSeconds(RateLimitAttempt attempt) {
        long now = System.currentTimeMillis();
        return Math.max(1, (attempt.getWindowStart() + WINDOW_MS - now) / 1000);
    }

    @Getter @Setter
    private static class RateLimitAttempt {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        public AtomicInteger count() {
            return count;
        }
    }
}
