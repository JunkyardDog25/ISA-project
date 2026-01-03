package com.example.jutjubic.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter implements Filter {

    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final long WINDOW_MS = 60_000L;

    private static class Attempt {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
    }

    private final Map<String, Attempt> requestCounts = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        String ip = httpRequest.getRemoteAddr();
        long now = System.currentTimeMillis();

        Attempt attempt = requestCounts.computeIfAbsent(ip, k -> new Attempt());

        if (now - attempt.windowStart > WINDOW_MS) {
            attempt.count.set(0);
            attempt.windowStart = now;
        }

        int currentCount = attempt.count.incrementAndGet();

        if (currentCount > MAX_REQUESTS_PER_MINUTE) {
            long retryAfterSeconds = Math.max(1, (attempt.windowStart + WINDOW_MS - now) / 1000);
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\\\"error\\\":\\\"Too many requests - try again later\\\",\\\"retryAfterSeconds\\\":\" + retryAfterSeconds + \"}");
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
