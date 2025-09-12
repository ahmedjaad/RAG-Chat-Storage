package com.rag.chatstorage.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OpenAiDefaultProfileContextTest {

    @Test
    void contextLoads_withDefaultProfile_hasSingleChatClientAndEmbeddingModel(ApplicationContext ctx) {
        String[] chatClients = ctx.getBeanNamesForType(ChatClient.class);
        String[] embeddings = ctx.getBeanNamesForType(EmbeddingModel.class);
        assertThat(chatClients).hasSize(1);
        assertThat(embeddings).hasSize(1);
    }
}
