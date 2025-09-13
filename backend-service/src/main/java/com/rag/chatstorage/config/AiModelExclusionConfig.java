package com.rag.chatstorage.config;

import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class AiModelExclusionConfig {
    @Configuration
    @Profile("!anthropic") // Apply this configuration when the "anthropic" profile is NOT active
    @EnableAutoConfiguration(exclude = {
            AnthropicChatAutoConfiguration.class
    })
    static class AnthropicExclusionConfig { }
}