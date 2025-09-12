package com.rag.chatstorage.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class SecurityConfig {

    @Value("${security.api-key.header:X-API-KEY}")
    private String apiKeyHeader;

    @Value("${security.api-key.value:changeme}")
    private String apiKeyValue;

    @Value("${rate-limit.requests-per-minute:60}")
    private int rpm;

    @Value("${rate-limit.burst:20}")
    private int burst;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public OncePerRequestFilter apiKeyFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                String path = request.getRequestURI();
                // Allow health, docs, swagger-ui, and simple UI without API key for convenience (can be changed)
                if ("/".equals(path)
                        || path.startsWith("/actuator/health")
                        || path.startsWith("/v3/api-docs")
                        || path.startsWith("/swagger-ui")
                        || path.equals("/docs")
                        || path.startsWith("/ui")
                        || path.equals("/favicon.ico")
                        || path.startsWith("/css/")
                        || path.startsWith("/js/")
                        || path.startsWith("/images/")
                        || path.startsWith("/assets/")
                        || path.startsWith("/webjars/")
                ) {
                    filterChain.doFilter(request, response);
                    return;
                }
                String key = request.getHeader(apiKeyHeader);
                if (!StringUtils.hasText(apiKeyValue) || !StringUtils.hasText(key) || !apiKeyValue.equals(key)) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    // Provide a helpful message indicating the expected header name
                    String msg = String.format("{\"error\":\"Unauthorized\",\"hint\":\"Send the API key in header %s matching server configuration. Set API_KEY in your .env.\"}", apiKeyHeader);
                    response.getWriter().write(msg);
                    // Optionally include a WWW-Authenticate hint (non-standard for API key, but helps tooling)
                    response.setHeader("WWW-Authenticate", "ApiKey realm=\"api\", header=\"" + apiKeyHeader + "\"");
                    return;
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    // Simple fixed-window per-key rate limiter (minute granularity) with burst cap
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    public OncePerRequestFilter rateLimitFilter() {
        final Map<String, Window> buckets = new ConcurrentHashMap<>();
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                String key = request.getRemoteAddr();
                String apiKey = request.getHeader(apiKeyHeader);
                if (StringUtils.hasText(apiKey)) {
                    key = apiKey; // rate limit per API key primarily
                }
                long minute = Instant.now().getEpochSecond() / 60;
                Window w = buckets.computeIfAbsent(key, k -> new Window(minute, new AtomicInteger(0)));
                synchronized (w) {
                    if (w.minute != minute) {
                        w.minute = minute;
                        w.counter.set(0);
                    }
                    int current = w.counter.incrementAndGet();
                    int limit = Math.max(rpm, burst);
                    int remaining = Math.max(0, limit - current);
                    // Always expose rate limit headers on every response
                    response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
                    response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
                    if (current > limit) {
                        // compute seconds until window resets
                        long nowSec = Instant.now().getEpochSecond();
                        long retryAfter = 60 - (nowSec % 60);
                        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                        response.setHeader("Retry-After", String.valueOf(retryAfter));
                        response.setContentType("application/problem+json");
                        String instance = request.getRequestURI();
                        String body = "{" +
                                "\"type\":\"about:blank\"," +
                                "\"title\":\"Too Many Requests\"," +
                                "\"status\":429," +
                                "\"detail\":\"Rate limit exceeded. Please retry later.\"," +
                                "\"instance\":\"" + instance + "\"," +
                                "\"code\":\"RATE_LIMITED\"," +
                                "\"limit\":" + limit + "," +
                                "\"remaining\":" + remaining + "," +
                                "\"retryAfterSeconds\":" + retryAfter +
                                "}";
                        response.getWriter().write(body);
                        return;
                    }
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    private static class Window {
        long minute;
        AtomicInteger counter;
        Window(long minute, AtomicInteger counter) { this.minute = minute; this.counter = counter; }
    }

    @Bean
    public WebMvcConfigurer corsConfigurer(
            @Value("${cors.allowed-origins:*}") String allowedOrigins,
            @Value("${cors.allowed-methods:GET,POST,PATCH,DELETE,OPTIONS}") String allowedMethods,
            @Value("${cors.allowed-headers:*}") String allowedHeaders
    ) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = allowedOrigins.split(",");
                boolean wildcard = origins.length == 1 && origins[0].trim().equals("*");
                var mapping = registry.addMapping("/**")
                        .allowedMethods(allowedMethods.split(","))
                        .allowedHeaders(allowedHeaders.split(","));
                if (wildcard) {
                    // When using wildcard origins, credentials must be disabled per CORS spec
                    mapping.allowedOriginPatterns("*").allowCredentials(false);
                } else {
                    mapping.allowedOrigins(origins).allowCredentials(true);
                }
            }
        };
    }
}
