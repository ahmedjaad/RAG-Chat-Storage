package com.rag.chatstorage.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

/**
 * Spring configuration that wires the distributed rate limiter backed by Redis (Bucket4j + Lettuce).
 * <p>
 * When ratelimit.redisEnabled=true, this registers a Redis connection and a ProxyManager bean.
 * If Redis is unavailable and failOnRedisUnavailable=false, the ProxyManager bean is omitted,
 * allowing the system to fall back to local in-memory buckets.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitingConfig implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(RateLimitingConfig.class);

    @Autowired
    private  RateLimitProperties props;

    /**
     * Creates a stateful Lettuce connection for Bucket4j to store bucket state in Redis.
     * The connection is configured using {@link RateLimitProperties}.
     *
     * @param props rate limiting properties used to build the Redis URI
     * @return a stateful Redis connection
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "ratelimit", name = "redisEnabled", havingValue = "true")
    public StatefulRedisConnection<byte[], byte[]> statefulRedisConnection(RateLimitProperties props) {
        RedisClient client = RedisClient.create(
                String.format("redis://%s%s@%s:%d/%d",
                        props.isSsl() ? "rediss" : "redis",
                        StringUtils.hasText(props.getPassword()) ? props.getPassword() + "@" : "",
                        props.getHost(),
                        props.getPort(),
                        props.getDatabase()
                )
        );
        return client.connect(new ByteArrayCodec());
    }
    /**
     * Builds a Bucket4j ProxyManager backed by Redis for distributed rate limiting.
     * If Redis is unavailable and {@code failOnRedisUnavailable} is false, the method logs a warning
     * and returns {@code null} so that Spring does not register the bean. This enables a local fallback.
     *
     * @param nativeConn the Redis connection
     * @param props rate limit configuration
     * @return a ProxyManager or {@code null} when falling back to local buckets
     */
    @Bean
    @ConditionalOnProperty(prefix = "ratelimit", name = "redisEnabled", havingValue = "true")
    public ProxyManager<byte[]> proxyManager(StatefulRedisConnection<byte[], byte[]> nativeConn, RateLimitProperties props) {
        try {
            return LettuceBasedProxyManager
                .builderFor(nativeConn)
                .withExpirationStrategy(
                    ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                        Duration.ofSeconds(props.getKeyTtlSeconds())
                    )
                )
                .build();
        } catch (Exception ex) {
            if (props.isFailOnRedisUnavailable()) {
                log.error("Redis connection failed and failOnRedisUnavailable=true. Failing startup.", ex);
                throw (RuntimeException) ex;
            }
            log.warn("Redis is unavailable at startup; distributed rate limiting will be disabled. Falling back to local buckets.");
            // Returning null from a @Bean method tells Spring not to register this bean.
            return null;
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (props.getDefaultPolicy() == null) {
            log.warn("Default Policy must be set for the Rate Limiter to work");
            throw new RuntimeException("Rate Limiter default policy not set");
        }

        validateDefaultPolicy(props.getDefaultPolicy());
    }

    private void validateDefaultPolicy(RateLimitProperties.Policy defaultPolicy) {
        List<String> errors = new ArrayList<>();
        AntPathMatcher pathMatcher = new AntPathMatcher();

        // Check if match section exists
        if (defaultPolicy.getMatch() == null) {
            errors.add("Default policy must have a 'match' section");
        } else {
            // Check methods
            if (defaultPolicy.getMatch().getMethods() == null || defaultPolicy.getMatch().getMethods().isEmpty()) {
                errors.add("Default policy match section must define 'methods' (cannot be null or empty)");
            } else {
                // Validate that all required HTTP methods are supported
                List<String> requiredMethods = Arrays.asList("GET", "POST", "PATCH", "DELETE");
                List<String> supportedMethods = defaultPolicy.getMatch().getMethods().stream()
                        .map(String::toUpperCase)
                        .toList();

                List<String> missingMethods = requiredMethods.stream()
                        .filter(method -> !supportedMethods.contains(method))
                        .toList();

                if (!missingMethods.isEmpty()) {
                    errors.add("Default policy must support all common HTTP methods. Missing: " + String.join(", ", missingMethods));
                }
            }

            // Check paths
            if (defaultPolicy.getMatch().getPaths() == null || defaultPolicy.getMatch().getPaths().isEmpty()) {
                errors.add("Default policy match section must define 'paths' (cannot be null or empty)");
            } else {
                // Validate that /api/** paths are covered
                boolean canHandleApiPaths = defaultPolicy.getMatch().getPaths().stream()
                        .anyMatch(pattern -> pathMatcher.match(pattern, "/api/test/endpoint"));

                if (!canHandleApiPaths) {
                    errors.add("Default policy must include a path pattern that matches '/api/**' endpoints (e.g., '/api/**')");
                }
            }
        }

        // Check bandwidths
        if (defaultPolicy.getBandwidths() == null || defaultPolicy.getBandwidths().isEmpty()) {
            errors.add("Default policy must define at least one bandwidth limit");
        } else {
            for (int i = 0; i < defaultPolicy.getBandwidths().size(); i++) {
                RateLimitProperties.BandwidthDef bandwidth = defaultPolicy.getBandwidths().get(i);
                if (bandwidth.getLimit() <= 0) {
                    errors.add("Default policy bandwidth[" + i + "] limit must be greater than 0");
                }
                if (bandwidth.getWindowSeconds() <= 0) {
                    errors.add("Default policy bandwidth[" + i + "] windowSeconds must be greater than 0");
                }
            }
        }

        if (!errors.isEmpty()) {
            String errorMessage = "Invalid default policy configuration:\n" + String.join("\n", errors);
            log.error(errorMessage);
            throw new RuntimeException("Rate Limiter default policy validation failed: " + String.join(", ", errors));
        }

        log.info("Default policy validation passed successfully - can handle all /api/** endpoints with GET, POST, PATCH, DELETE methods");
    }
}
