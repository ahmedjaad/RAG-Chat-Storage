package com.rag.chatstorage.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.annotation.Resource;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AiControllerSmokeTest {

    @Resource
    MockMvc mockMvc;

    @MockBean
    ChatClient chatClient;

    @MockBean
    EmbeddingModel embeddingModel;

    @Resource
    ObjectMapper objectMapper;

    @Test
    void infer_endpoint_shape() throws Exception {
        when(chatClient.prompt().user("Hello").call().content()).thenReturn("Hi there!");
        String body = objectMapper.writeValueAsString(new com.fasterxml.jackson.databind.node.ObjectNode(objectMapper.getNodeFactory())
                .put("prompt", "Hello"));
        mockMvc.perform(post("/api/v1/ai/infer")
                        .header("X-API-KEY", "changeme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hi there!"));
    }

    @Test
    void embeddings_endpoint_shape() throws Exception {
        org.springframework.ai.embedding.EmbeddingResponse resp = new org.springframework.ai.embedding.EmbeddingResponse(
                java.util.List.of(new org.springframework.ai.embedding.EmbeddingResponse.Embedding("id", new float[]{1f,2f,3f}, null)),
                null
        );
        when(embeddingModel.embedForResponse(anyList())).thenReturn(resp);
        String body = objectMapper.writeValueAsString(new com.fasterxml.jackson.databind.node.ObjectNode(objectMapper.getNodeFactory())
                .set("inputs", objectMapper.createArrayNode().add("a").add("b")));
        mockMvc.perform(post("/api/v1/ai/embeddings")
                        .header("X-API-KEY", "changeme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.dimensions").value(3));
    }
}
