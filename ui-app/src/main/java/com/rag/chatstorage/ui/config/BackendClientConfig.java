package com.rag.chatstorage.ui.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class BackendClientConfig {

    @Bean
    public WebClient backendWebClient(@Value("${backend.base-url:http://localhost:8080}") String baseUrl,
                                      @Value("${backend.api-key:changeme}") String apiKey,
                                      WebClient.Builder builder) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("X-API-KEY", apiKey)
                .build();
    }
}
