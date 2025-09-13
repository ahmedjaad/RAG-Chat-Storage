package com.rag.chatstorage.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class AppHealthIndicator implements HealthIndicator {

    @Value("${spring.application.name:RAG Chat Storage}")
    private String appName;

    @Value("${security.api-key.header:X-API-KEY}")
    private String apiKeyHeader;

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        details.put("app", appName);
        details.put("time", Instant.now().toString());
        details.put("authHeader", apiKeyHeader);
        return Health.up().withDetails(details).build();
    }
}
