package com.rag.chatstorage.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

@Configuration
public class SecurityConfig {

    @Value("${security.api-key.header:X-API-KEY}")
    private String apiKeyHeader;

    @Value("${security.api-key.values:}")
    private String apiKeyValues;

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
                        || path.startsWith("/h2-console")
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
                boolean authorized = false;
                if (StringUtils.hasText(key)) {
                    // Check list values
                    if (StringUtils.hasText(apiKeyValues)) {
                        for (String v : apiKeyValues.split(",")) {
                            if (key.equals(v.trim()) && !v.trim().isEmpty()) { authorized = true; break; }
                        }
                    }
                }
                if (!authorized) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    // Provide a helpful message indicating the expected header name
                    String msg = String.format("{\"error\":\"Unauthorized\",\"hint\":\"Send the API key in header %s matching server configuration. Set API_KEYS in your .env.\"}", apiKeyHeader);
                    response.getWriter().write(msg);
                    // Optionally include a WWW-Authenticate hint (non-standard for API key, but helps tooling)
                    response.setHeader("WWW-Authenticate", "ApiKey realm=\"api\", header=\"" + apiKeyHeader + "\"");
                    return;
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    // Rate limiting is now handled by distributed Bucket4j filter in com.rag.chatstorage.ratelimit.RateLimitFilter

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

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 3)
    public OncePerRequestFilter securityHeadersFilter() {
        final String csp = "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'";
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("Referrer-Policy", "no-referrer");
                // Apply CSP to all responses (primarily affects UI pages)
                response.setHeader("Content-Security-Policy", csp);
                filterChain.doFilter(request, response);
            }
        };
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 0)
    public OncePerRequestFilter requestSizeLimitFilter(
            @Value("${request.max-bytes:0}") long maxBytes
    ) {
        // If maxBytes <= 0, do nothing
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                if (maxBytes > 0) {
                    String cl = request.getHeader("Content-Length");
                    if (cl != null) {
                        try {
                            long contentLength = Long.parseLong(cl);
                            if (contentLength > maxBytes) {
                                response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
                                response.setContentType("application/problem+json");
                                String instance = request.getRequestURI();
                                String body = "{" +
                                        "\"type\":\"about:blank\"," +
                                        "\"title\":\"Payload Too Large\"," +
                                        "\"status\":413," +
                                        "\"detail\":\"Request payload exceeds allowed size.\"," +
                                        "\"instance\":\"" + instance + "\"," +
                                        "\"code\":\"PAYLOAD_TOO_LARGE\"" +
                                        "}";
                                response.getWriter().write(body);
                                return;
                            }
                        } catch (NumberFormatException ignored) { }
                    }
                }
                filterChain.doFilter(request, response);
            }
        };
    }
}
