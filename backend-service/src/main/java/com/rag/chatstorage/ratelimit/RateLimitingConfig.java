package com.rag.chatstorage.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(prefix = "ratelimit", name = "redisEnabled", havingValue = "true")
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitingConfig {
    private static final Logger log = LoggerFactory.getLogger(RateLimitingConfig.class);

    @Bean
    public RedisConnectionFactory redisConnectionFactory(RateLimitProperties props) {
        LettuceConnectionFactory f = new LettuceConnectionFactory(props.getHost(), props.getPort());
        if (StringUtils.hasText(props.getPassword())) {
            f.setPassword(props.getPassword());
        }
        f.setDatabase(props.getDatabase());
        f.setUseSsl(props.isSsl());
        f.afterPropertiesSet();
        return f;
    }

    @Bean
    public ProxyManager<byte[]> proxyManager(RedisConnectionFactory factory, RateLimitProperties props) {
        // Ensure we only proceed if the underlying factory is Lettuce-based
        if (!(factory instanceof LettuceConnectionFactory lettuceFactory)) {
            throw new IllegalStateException("RedisConnectionFactory is not LettuceConnectionFactory. Please configure lettuce if RATELIMIT_REDIS_ENABLED=true");
        }
        try {
            @SuppressWarnings("unchecked")
            StatefulRedisConnection<byte[], byte[]> nativeConn = (StatefulRedisConnection<byte[], byte[]>) lettuceFactory.getConnection().getNativeConnection();
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
                throw ex instanceof RuntimeException re ? re : new RuntimeException(ex);
            }
            log.warn("Redis is unavailable at startup; distributed rate limiting will be disabled. Falling back to local buckets.");
            // Returning null from a @Bean method tells Spring not to register this bean.
            return null;
        }
    }
}
