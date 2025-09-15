package com.rag.chatstorage.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties props;
    private final ProxyManager<byte[]> proxyManager;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final MeterRegistry meterRegistry;
    private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    @Value("${security.api-key.header:X-API-KEY}")
    private String apiKeyHeader;

    public RateLimitFilter(RateLimitProperties props, ObjectProvider<ProxyManager<byte[]>> proxyManagerProvider, MeterRegistry meterRegistry) {
        this.props = props;
        this.proxyManager = proxyManagerProvider.getIfAvailable();
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) return true;
        String path = request.getRequestURI();
        for (String p : props.getWhitelistPaths()) {
            if (matcher.match(p, path)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();

        String subject = resolveSubject(request);
        String tier = props.getApiKeys().getOrDefault(getApiKey(request), props.getDefaultTier());

        RateLimitProperties.Policy policy = selectPolicy(method, path, tier);
        if (policy == null) {
            // Default deny-less policy: no rate limiting applied
            filterChain.doFilter(request, response);
            return;
        }
        int cost = computeCost(policy, method, path);

        BucketConfiguration config = toBucketConfig(policy.getBandwidths());
        byte[] key = buildKey(props.getKeyPrefix(), tier, subject, method, normalizePath(path)).getBytes(StandardCharsets.UTF_8);
        ConsumptionProbe probe;
        if (proxyAvailable()) {
            try {
                BucketProxy bucket = proxyManager.builder().build(key, ()-> config);
                probe = bucket.tryConsumeAndReturnRemaining(cost);
            } catch (Exception ex) {
                // Fallback to local in-memory bucket with stricter limits
                String localKey = new String(key, StandardCharsets.UTF_8);
                Bucket local = localBuckets.computeIfAbsent(localKey, k -> {
                    io.github.bucket4j.local.LocalBucketBuilder lb = Bucket.builder();
                    for (Bandwidth bw : adjustForFallback(policy.getBandwidths())) {
                        lb.addLimit(bw);
                    }
                    return lb.build();
                });
                probe = local.tryConsumeAndReturnRemaining(cost);
                log.warn("Redis unavailable, using local fallback rate limiter: {}", ex.toString());
            }
        } else {
            // No proxy manager configured; use local bucket without exceptions
            String localKey = new String(key, StandardCharsets.UTF_8);
            Bucket local = localBuckets.computeIfAbsent(localKey, k -> {
                io.github.bucket4j.local.LocalBucketBuilder lb = Bucket.builder();
                for (Bandwidth bw : adjustForFallback(policy.getBandwidths())) {
                    lb.addLimit(bw);
                }
                return lb.build();
            });
            probe = local.tryConsumeAndReturnRemaining(cost);
        }
        addHeaders(response, policy, probe, cost);
        recordMetrics(method, path, tier, probe.isConsumed());
        if (!probe.isConsumed()) {
            long retryAfter = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
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
                    "\"limit\":" + mostRestrictiveLimit(policy) + "," +
                    "\"remaining\":" + probe.getRemainingTokens() + "," +
                    "\"reset\":" + retryAfter + "," +
                    "\"subject\":\"" + sanitize(subject) + "\"," +
                    "\"tier\":\"" + sanitize(tier) + "\"" +
                    "}";
            response.getWriter().write(body);
            log.warn("rate_limit_blocked subject={} tier={} method={} path={} remaining={} retryAfter={}s", subject, tier, method, path, probe.getRemainingTokens(), retryAfter);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String sanitize(String s) { return s == null ? "unknown" : s.replaceAll("[^a-zA-Z0-9:_-]", "?"); }

    private void addHeaders(HttpServletResponse response, RateLimitProperties.Policy policy, ConsumptionProbe probe, int cost) {
        // RateLimit-Limit: send per bandwidth values
        List<String> limits = new ArrayList<>();
        for (RateLimitProperties.BandwidthDef b : policy.getBandwidths()) {
            limits.add(b.getLimit() + ";w=" + b.getWindowSeconds());
        }
        response.setHeader("RateLimit-Limit", String.join(",", limits));
        // Remaining and Reset reflect most restrictive window
        long reset = probe.isConsumed() ? probe.getNanosToWaitForRefill() / 1_000_000_000L : Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        response.setHeader("RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        response.setHeader("RateLimit-Reset", String.valueOf(reset));
        // Legacy headers for short window (assume first bandwidth is short window)
        if (!policy.getBandwidths().isEmpty()) {
            RateLimitProperties.BandwidthDef first = policy.getBandwidths().get(0);
            response.setHeader("X-RateLimit-Limit", String.valueOf(first.getLimit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, probe.getRemainingTokens())));
        }
    }

    private String getApiKey(HttpServletRequest request) {
        return request.getHeader(apiKeyHeader);
    }

    private String resolveSubject(HttpServletRequest request) {
        String apiKey = getApiKey(request);
        if (StringUtils.hasText(apiKey)) return "key:" + apiKey;
        String ip = request.getRemoteAddr();
        // Optionally read X-Forwarded-For if trusted
        if (props.isTrustProxies()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                ip = xff.split(",")[0].trim();
            }
        }
        if (!StringUtils.hasText(ip)) {
            return "anon:unknown";
        }
        return "ip:" + ip;
    }

    private RateLimitProperties.Policy selectPolicy(String method, String path, String tier) {
        RateLimitProperties.Policy fallback = null;
        for (RateLimitProperties.Policy p : props.getPolicies()) {
            if (p.getTier() != null && !p.getTier().equalsIgnoreCase(tier)) continue;
            if (matches(p, method, path)) return p;
            if (p.getTier() == null && matches(p, method, path)) fallback = p;
        }
        return fallback;
    }
    private boolean matches(RateLimitProperties.Policy p, String method, String path) {
        if (p.getMatch() == null) return true;
        boolean methodOk = p.getMatch().getMethods() == null || p.getMatch().getMethods().isEmpty() || p.getMatch().getMethods().stream().anyMatch(m -> m.equalsIgnoreCase(method));
        boolean pathOk = p.getMatch().getPaths() == null || p.getMatch().getPaths().isEmpty() || p.getMatch().getPaths().stream().anyMatch(ptn -> matcher.match(ptn, path));
        return methodOk && pathOk;
    }

    private int computeCost(RateLimitProperties.Policy p, String method, String path) {
        if (p.getCosts() == null) return 1;
        return p.getCosts().stream()
                .filter(c -> (c.getMethod() == null || c.getMethod().equalsIgnoreCase(method)) && matcher.match(c.getPath(), path))
                .map(RateLimitProperties.Cost::getTokens)
                .findFirst().orElse(1);
    }

    private BucketConfiguration toBucketConfig(List<RateLimitProperties.BandwidthDef> defs) {
        var builder = BucketConfiguration.builder();
        for (Bandwidth bw : adjustForFallback(defs)) {
            builder.addLimit(bw);
        }
        return builder.build();
    }

    private List<Bandwidth> adjustForFallback(List<RateLimitProperties.BandwidthDef> defs) {
        double factor = props.getFallbackFactor() > 0 && props.getFallbackFactor() <= 1 ? props.getFallbackFactor() : 0.5;
        List<Bandwidth> list = new ArrayList<>();
        for (RateLimitProperties.BandwidthDef d : defs) {
            long limit = Math.max(1, (long) Math.floor(d.getLimit() * (proxyAvailable() ? 1.0 : factor)));
            io.github.bucket4j.Refill refill = "interval".equalsIgnoreCase(d.getRefillStrategy())
                    ? io.github.bucket4j.Refill.intervally(limit, Duration.ofSeconds(d.getWindowSeconds()))
                    : io.github.bucket4j.Refill.greedy(limit, Duration.ofSeconds(d.getWindowSeconds()));
            list.add(Bandwidth.classic(limit, refill));
        }
        return list;
    }

    private boolean proxyAvailable() {
        try {
            return proxyManager != null;
        } catch (Exception e) {
            return false;
        }
    }


    private String normalizePath(String path) {
        // Basic normalization: replace numeric path segments with {id}
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].matches("\\d+")) parts[i] = "{id}";
        }
        return String.join("/", parts);
    }

    private String buildKey(String prefix, String tier, String subject, String method, String normalizedPath) {
        return (prefix == null ? "ratelimit:" : prefix) + tier + ":" + subject + ":" + method + ":" + normalizedPath;
    }

    private long mostRestrictiveLimit(RateLimitProperties.Policy p) {
        return p.getBandwidths().stream().mapToLong(RateLimitProperties.BandwidthDef::getLimit).min().orElse(0);
    }

    private void recordMetrics(String method, String path, String tier, boolean allowed) {
        String endpoint = normalizePath(path);
        meterRegistry.counter("counter.ratelimit.requests", "endpoint", endpoint, "method", method, "tier", tier, "outcome", allowed ? "allowed" : "blocked").increment();
        if (!allowed) {
            meterRegistry.counter("counter.ratelimit.exceeded", "endpoint", endpoint, "method", method, "tier", tier).increment();
        }
    }
}
