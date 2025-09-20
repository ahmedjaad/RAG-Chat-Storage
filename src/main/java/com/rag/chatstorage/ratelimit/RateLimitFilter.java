package com.rag.chatstorage.ratelimit;

import com.rag.chatstorage.ratelimit.RateLimitProperties.Policy;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.local.LocalBucketBuilder;
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

/**
 * Servlet filter that enforces API rate limits based on {@link RateLimitProperties}.
 * <p>
 * The filter evaluates incoming requests against configured policies, attempts to consume tokens
 * from a distributed Redis-backed bucket (when available), and falls back to local in-memory
 * buckets if Redis is unavailable or not configured. Standard RateLimit headers are added to
 * responses and a 429 Problem+JSON body is returned when limits are exceeded.
 */
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

    /**
     * Creates a new RateLimitFilter.
     *
     * @param props rate limit configuration properties
     * @param proxyManagerProvider optional provider for the distributed Bucket4j ProxyManager (may be absent to enable local fallback)
     * @param meterRegistry Micrometer registry used to record allow/block metrics
     */
    public RateLimitFilter(RateLimitProperties props, ObjectProvider<ProxyManager<byte[]>> proxyManagerProvider, MeterRegistry meterRegistry) {
        this.props = props;
        this.proxyManager = proxyManagerProvider.getIfAvailable();
        this.meterRegistry = meterRegistry;
    }

    @Override
    /*
     * Determines whether this request should bypass rate limiting based on configuration.
     */
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) return true;
        String path = request.getRequestURI();
        for (String p : props.getWhitelistPaths()) {
            if (matcher.match(p, path)) return true;
        }
        return false;
    }

    @Override
    /*
     * Core filter logic that selects the matching policy, consumes tokens from a bucket,
     * adds response headers, and returns 429 when the request exceeds its budget.
     */
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();

        String subject = resolveSubject(request);
        String apiKey = getApiKey(request);
        String tier;
        if (StringUtils.hasText(apiKey) && props.getApiKeyTiers().containsKey(apiKey)) {
            tier = props.getApiKeyTiers().get(apiKey);
        } else {
            tier = props.getDefaultTier();
        }

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
                probe = consumptionProbe(policy, cost, key);
                log.warn("Redis unavailable, using local fallback rate limiter: {}", ex.toString());
            }
        } else {
            // No proxy manager configured; use local bucket without exceptions
            probe = consumptionProbe(policy, cost, key);
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

    private ConsumptionProbe consumptionProbe(Policy policy, int cost, byte[] key) {
        ConsumptionProbe probe;
        String localKey = new String(key, StandardCharsets.UTF_8);
        Bucket local = localBuckets.computeIfAbsent(localKey, k -> {
            LocalBucketBuilder lb = Bucket.builder();
            for (Bandwidth bw : adjustForFallback(policy.getBandwidths())) {
                lb.addLimit(bw);
            }
            return lb.build();
        });
        probe = local.tryConsumeAndReturnRemaining(cost);
        return probe;
    }

    /*
     * Normalizes potentially unsafe values for logging/headers by stripping non-alphanumerics.
     */
    private String sanitize(String s) { return s == null ? "unknown" : s.replaceAll("[^a-zA-Z0-9:_-]", "?"); }

    /*
     * Populates standard RateLimit headers and legacy X-RateLimit-* headers on the response.
     */
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
            RateLimitProperties.BandwidthDef first = policy.getBandwidths().getFirst();
            response.setHeader("X-RateLimit-Limit", String.valueOf(first.getLimit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, probe.getRemainingTokens())));
        }
    }

    /*
     * Extracts API key from the configured header.
     */
    private String getApiKey(HttpServletRequest request) {
        return request.getHeader(apiKeyHeader);
    }

    /*
     * Resolves the rate limiting subject (API key or IP address) for the request.
     */
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

    /*
     * Selects the first matching policy for the given method/path and tier; uses defaultPolicy as fallback.
     */
    private RateLimitProperties.Policy selectPolicy(String method, String path, String tier) {
        // First, check if any specific policy matches (non-default tier policies)
        for (RateLimitProperties.Policy p : props.getPolicies()) {
            if (p.getTier() != null && !p.getTier().equalsIgnoreCase(tier)) continue;
            if (matches(p, method, path)) return p;
        }

        // If no specific policy matches, use the defaultPolicy as fallback
        return props.getDefaultPolicy();
    }

    /*
     * Checks if a request method and path match the policy's method/path patterns.
     * Returns false if match criteria are missing or incomplete.
     */
    private boolean matches(RateLimitProperties.Policy p, String method, String path) {
        if (p.getMatch() == null) return false;
        if (p.getMatch().getMethods() == null || p.getMatch().getMethods().isEmpty()) return false;
        if (p.getMatch().getPaths() == null || p.getMatch().getPaths().isEmpty()) return false;

        boolean methodOk = p.getMatch().getMethods().stream().anyMatch(m -> m.equalsIgnoreCase(method));
        boolean pathOk = p.getMatch().getPaths().stream().anyMatch(ptn -> matcher.match(ptn, path));
        return methodOk && pathOk;
    }

    /*
     * Computes the token cost for a request based on policy overrides; defaults to 1.
     */
    private int computeCost(RateLimitProperties.Policy p, String method, String path) {
        if (p.getCosts() == null) return 1;
        return p.getCosts().stream()
                .filter(c -> (c.getMethod() == null || c.getMethod().equalsIgnoreCase(method)) && matcher.match(c.getPath(), path))
                .map(RateLimitProperties.Cost::getTokens)
                .findFirst().orElse(1);
    }

    /*
     * Builds a Bucket4j configuration from a list of bandwidth definitions, considering fallback adjustments.
     */
    private BucketConfiguration toBucketConfig(List<RateLimitProperties.BandwidthDef> defs) {
        var builder = BucketConfiguration.builder();
        for (Bandwidth bw : adjustForFallback(defs)) {
            builder.addLimit(bw);
        }
        return builder.build();
    }

    /*
     * Adjusts bandwidth limits when using local fallback, making them stricter based on fallbackFactor.
     */
    private List<Bandwidth> adjustForFallback(List<RateLimitProperties.BandwidthDef> defs) {
        double factor = props.getFallbackFactor() > 0 && props.getFallbackFactor() <= 1 ? props.getFallbackFactor() : 0.5;
        List<Bandwidth> list = new ArrayList<>();
        for (RateLimitProperties.BandwidthDef d : defs) {
            long limit = Math.max(1, (long) Math.floor(d.getLimit() * (proxyAvailable() ? 1.0 : factor)));
            Refill refill = "interval".equalsIgnoreCase(d.getRefillStrategy())
                    ? Refill.intervally(limit, Duration.ofSeconds(d.getWindowSeconds()))
                    : Refill.greedy(limit, Duration.ofSeconds(d.getWindowSeconds()));
            list.add(Bandwidth.classic(limit, refill));
        }
        return list;
    }

    /*
     * Checks if a distributed ProxyManager is available for Redis-backed buckets.
     */
    private boolean proxyAvailable() {
        try {
            return proxyManager != null;
        } catch (Exception e) {
            return false;
        }
    }


    /*
     * Normalizes dynamic path segments (e.g., numeric IDs) to reduce cardinality for keys/metrics.
     */
    private String normalizePath(String path) {
        // Basic normalization: replace numeric path segments with {id}
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].matches("\\d+")) parts[i] = "{id}";
        }
        return String.join("/", parts);
    }

    /*
     * Builds a deterministic bucket key from tier, subject, method and normalized path.
     */
    private String buildKey(String prefix, String tier, String subject, String method, String normalizedPath) {
        return (prefix == null ? "ratelimit:" : prefix) + tier + ":" + subject + ":" + method + ":" + normalizedPath;
    }

    /*
     * Returns the smallest limit among a policy's bandwidth windows.
     */
    private long mostRestrictiveLimit(RateLimitProperties.Policy p) {
        return p.getBandwidths().stream().mapToLong(RateLimitProperties.BandwidthDef::getLimit).min().orElse(0);
    }

    /*
     * Records Micrometer counters for allowed/blocked requests per normalized endpoint, method and tier.
     */
    private void recordMetrics(String method, String path, String tier, boolean allowed) {
        String endpoint = normalizePath(path);
        meterRegistry.counter("counter.ratelimit.requests", "endpoint", endpoint, "method", method, "tier", tier, "outcome", allowed ? "allowed" : "blocked").increment();
        if (!allowed) {
            meterRegistry.counter("counter.ratelimit.exceeded", "endpoint", endpoint, "method", method, "tier", tier).increment();
        }
    }
}
