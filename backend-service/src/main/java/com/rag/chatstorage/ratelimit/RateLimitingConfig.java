package com.rag.chatstorage.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.ratelimit.enabled", havingValue = "true")
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
    public ProxyManager<byte[]> proxyManager(LettuceConnectionFactory factory, RateLimitProperties props) {
        @SuppressWarnings("unchecked")
        StatefulRedisConnection<byte[], byte[]> nativeConn = (StatefulRedisConnection<byte[], byte[]>) factory.getConnection().getNativeConnection();
        return LettuceBasedProxyManager
            .builderFor(nativeConn)
            .withExpirationStrategy(
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                    Duration.ofSeconds(props.getKeyTtlSeconds())
                )
            )
            .build();
    }
}
