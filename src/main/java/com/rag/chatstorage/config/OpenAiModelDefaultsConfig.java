package com.rag.chatstorage.config;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiModelDefaultsConfig {

    @Bean
    public OpenAiChatOptions defaultOpenAiChatOptions() {
        // These act as defaults for ChatClient when not explicitly set
        return OpenAiChatOptions.builder()
                .temperature(0.2d)
                .maxTokens(512)
                .build();
    }
}
