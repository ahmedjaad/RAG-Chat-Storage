package com.rag.chatstorage.config.provider;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("azure-openai")
public class AzureOpenAiProviderConfig {

    @Bean
    public ChatClient chatClient(AzureOpenAiChatModel azureOpenAiChatModel) {
        return ChatClient.create(azureOpenAiChatModel);
    }

    @Bean
    public EmbeddingModel embeddingModel(AzureOpenAiEmbeddingModel azureOpenAiEmbeddingModel) {
        return azureOpenAiEmbeddingModel;
    }
}
