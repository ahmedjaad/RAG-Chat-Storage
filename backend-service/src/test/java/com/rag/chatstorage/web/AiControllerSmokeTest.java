package com.rag.chatstorage.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.mockito.Answers;
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

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;

    @MockBean
    EmbeddingModel embeddingModel;

    @Resource
    ObjectMapper objectMapper;

    @Test
    void infer_endpoint_shape() throws Exception {
        // With deep-stubbed @MockBean, we can stub the fluent chain directly
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
        // Reflectively build EmbeddingResponse to tolerate Spring AI API variations
        org.springframework.ai.embedding.EmbeddingResponse resp = buildEmbeddingResponseReflective(3);
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

    // Helper that uses reflection to construct a minimal EmbeddingResponse
    private org.springframework.ai.embedding.EmbeddingResponse buildEmbeddingResponseReflective(int dims) {
        try {
            Class<?> erClass = org.springframework.ai.embedding.EmbeddingResponse.class;
            // Find inner type with a float[] parameter in a constructor
            Class<?> innerType = null;
            for (Class<?> c : erClass.getDeclaredClasses()) {
                for (var ctor : c.getDeclaredConstructors()) {
                    Class<?>[] p = ctor.getParameterTypes();
                    boolean hasFloatArray = java.util.Arrays.stream(p).anyMatch(t -> t.isArray() && t.getComponentType() == float.class);
                    if (hasFloatArray) { innerType = c; break; }
                }
                if (innerType != null) break;
            }
            if (innerType == null) throw new IllegalStateException("No suitable inner type found in EmbeddingResponse");
            Object innerInstance = null;
            for (var ctor : innerType.getDeclaredConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                // Try common signature: (String, float[], Map)
                if (p.length >= 2 && p[0] == String.class && p[1].isArray() && p[1].getComponentType() == float.class) {
                    ctor.setAccessible(true);
                    float[] vec = new float[dims];
                    for (int i = 0; i < dims; i++) vec[i] = i + 1;
                    Object[] args = new Object[p.length];
                    args[0] = "id";
                    args[1] = vec;
                    for (int i = 2; i < p.length; i++) args[i] = null;
                    innerInstance = ctor.newInstance(args);
                    break;
                }
            }
            if (innerInstance == null) throw new IllegalStateException("Could not instantiate inner embedding result");
            java.util.List<Object> list = java.util.List.of(innerInstance);
            // Find EmbeddingResponse constructor that accepts (List, ...)
            for (var ctor : erClass.getDeclaredConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length >= 1 && java.util.List.class.isAssignableFrom(p[0])) {
                    ctor.setAccessible(true);
                    Object[] args = new Object[p.length];
                    args[0] = list;
                    for (int i = 1; i < p.length; i++) args[i] = null;
                    return (org.springframework.ai.embedding.EmbeddingResponse) ctor.newInstance(args);
                }
            }
            throw new IllegalStateException("No suitable EmbeddingResponse constructor found");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
