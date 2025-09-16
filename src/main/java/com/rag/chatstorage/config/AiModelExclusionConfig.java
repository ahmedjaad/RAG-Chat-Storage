package com.rag.chatstorage.config;

import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
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

    @Configuration
    @Profile("!ollama") // Apply this configuration when the "ollama" profile is NOT active
    @EnableAutoConfiguration(exclude = {
            OllamaEmbeddingAutoConfiguration.class,
            OllamaChatAutoConfiguration.class
    })
    static class OllamaExclusionConfig { }

    @Configuration
    @Profile("!openai") // Apply this configuration when the "openai" profile is NOT active
    @EnableAutoConfiguration(
            exclude = {
            OpenAiChatAutoConfiguration.class,
            OpenAiModerationAutoConfiguration.class,
            OpenAiImageAutoConfiguration.class,
            OpenAiEmbeddingAutoConfiguration.class,
            OpenAiAudioTranscriptionAutoConfiguration.class,
            OpenAiAudioSpeechAutoConfiguration.class

    })
    static class OpenAIExclusionConfig { }
}