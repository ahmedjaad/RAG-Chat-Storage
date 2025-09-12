package com.rag.chatstorage.config.provider;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Default provider configuration: OpenAI
 * Active when none of the other provider profiles is selected.
 */
@Configuration
@Profile({"openai", "!anthropic & !ollama & !azure-openai & !openai-compatible"})
public class DefaultOpenAiProviderConfig {

    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.create(openAiChatModel);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public EmbeddingModel embeddingModel(OpenAiEmbeddingModel openAiEmbeddingModel) {
        return openAiEmbeddingModel;
    }
}
