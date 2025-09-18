package com.rag.chatstorage.service;

import com.rag.chatstorage.service.impl.SimpleAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AiServiceTest {

    private ChatClient chatClient;
    private AiService aiService;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        aiService = new SimpleAiService(chatClient);
    }

    @Test
    void infer_mapsGenericErrorToFriendly() {
        when(chatClient.prompt().user("hello").call().content()).thenThrow(new RuntimeException("500 Internal Server Error"));

        assertThatThrownBy(() -> aiService.infer(null, "hello"))
                .isInstanceOf(AiService.AiFriendlyException.class)
                .hasMessageContaining("couldn’t respond")
                .satisfies(ex -> {
                    AiService.AiFriendlyException e = (AiService.AiFriendlyException) ex;
                    assertThat(e.getCode()).isEqualTo("AI_UNAVAILABLE");
                    assertThat(e.getHint()).isIn("(500 server error)", null);
                });
    }

    @Test
    void infer_mapsTimeoutToFriendly() {
        when(chatClient.prompt().user("hello").call().content()).thenThrow(new RuntimeException("Read timeout"));

        assertThatThrownBy(() -> aiService.infer(null, "hello"))
                .isInstanceOf(AiService.AiFriendlyException.class)
                .hasMessageContaining("We could not reach the AI service");
    }

    @Test
    void infer_mapsUnauthorizedToFriendly() {
        when(chatClient.prompt().user("hello").call().content()).thenThrow(new RuntimeException("401 Unauthorized - invalid api key"));

        assertThatThrownBy(() -> aiService.infer(null, "hello"))
                .isInstanceOf(AiService.AiFriendlyException.class)
                .hasMessageContaining("AI credentials are invalid");
    }
}
