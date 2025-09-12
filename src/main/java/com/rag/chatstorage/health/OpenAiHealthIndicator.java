package com.rag.chatstorage.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Component("openai")
public class OpenAiHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry cbRegistry;
    private final ChatClient chatClient;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public OpenAiHealthIndicator(CircuitBreakerRegistry cbRegistry, ChatClient chatClient) {
        this.cbRegistry = cbRegistry;
        this.chatClient = chatClient;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        details.put("time", OffsetDateTime.now().toString());
        if (!StringUtils.hasText(apiKey)) {
            return Health.outOfService().withDetails(details).withDetail("reason", "missing api key").build();
        }

        CircuitBreaker cb = cbRegistry.circuitBreaker("openai");
        details.put("circuitState", cb.getState().name());
        switch (cb.getState()) {
            case OPEN, FORCED_OPEN -> {
                return Health.outOfService().withDetails(details).withDetail("reason", "circuit open").build();
            }
            default -> {
                // Lightweight sanity check only if recently healthy: build a prompt but do not call
                // We rely on recent failures captured by the circuit breaker for health signal.
                return Health.up().withDetails(details).build();
            }
        }
    }
}
