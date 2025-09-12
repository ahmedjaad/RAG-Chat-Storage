package com.rag.chatstorage.ratelimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Configuration
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
        var lettuceConn = (LettuceConnectionFactory) factory;
        var client = lettuceConn.getClientResources();
        StatefulRedisConnection<String, byte[]> connection = lettuceConn.getClientConfiguration().isUseSsl() ? null : null; // will be created by LettuceBasedProxyManager
        return LettuceBasedProxyManager.builderFor(factory).withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(props.getKeyTtlSeconds()))).build();
    }
}
