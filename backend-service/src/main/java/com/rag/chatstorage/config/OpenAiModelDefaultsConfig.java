package com.rag.chatstorage.config;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@org.springframework.context.annotation.Profile({"openai", "!anthropic & !ollama & !azure-openai & !openai-compatible"})
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
