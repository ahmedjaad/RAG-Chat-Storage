package com.rag.chatstorage.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component("ai")
@ConditionalOnBean(ChatClient.class)
public class OpenAiHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry cbRegistry;
    private final ChatClient chatClient;

    public OpenAiHealthIndicator(CircuitBreakerRegistry cbRegistry, ChatClient chatClient) {
        this.cbRegistry = cbRegistry;
        this.chatClient = chatClient;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        details.put("time", Instant.now().toString());

        CircuitBreaker cb = cbRegistry.circuitBreaker("ai");
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
