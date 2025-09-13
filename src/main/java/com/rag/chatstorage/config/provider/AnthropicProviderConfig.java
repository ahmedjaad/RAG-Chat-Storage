package com.rag.chatstorage.config.provider;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Anthropic for chat; OpenAI for embeddings (Anthropic does not provide embeddings).
 */
@Configuration
@Profile("anthropic")
public class AnthropicProviderConfig {

    @Bean
    public ChatClient chatClient(AnthropicChatModel anthropicChatModel) {
        return ChatClient.create(anthropicChatModel);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public EmbeddingModel embeddingModel(OpenAiEmbeddingModel openAiEmbeddingModel) {
        return openAiEmbeddingModel;
    }
}
