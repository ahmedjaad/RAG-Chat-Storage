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
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitingConfig {
    private static final Logger log = LoggerFactory.getLogger(RateLimitingConfig.class);

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
