package com.rag.chatstorage.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "ratelimit", name = "redisEnabled", havingValue = "true")
/**
 * Spring configuration that wires the distributed rate limiter backed by Redis (Bucket4j + Lettuce).
 * <p>
 * When ratelimit.redisEnabled=true, this registers a Redis connection and a ProxyManager bean.
 * If Redis is unavailable and failOnRedisUnavailable=false, the ProxyManager bean is omitted,
 * allowing the system to fall back to local in-memory buckets.
 */
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitingConfig {
    private static final Logger log = LoggerFactory.getLogger(RateLimitingConfig.class);

    /**
     * Creates a stateful Lettuce connection for Bucket4j to store bucket state in Redis.
     * The connection is configured using {@link RateLimitProperties}.
     *
     * @param props rate limiting properties used to build the Redis URI
     * @return a stateful Redis connection
     */
    @Bean(destroyMethod = "close")
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
}
