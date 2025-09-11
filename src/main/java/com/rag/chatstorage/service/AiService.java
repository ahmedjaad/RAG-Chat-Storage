package com.rag.chatstorage.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiService {

    public static class AiFriendlyException extends RuntimeException {
        private final String code;
        public AiFriendlyException(String code, String message) {
            super(message);
            this.code = code;
        }
        public String getCode() { return code; }
    }

    private final ChatClient chatClient;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public AiService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String infer(String system, String user) {
        if (!StringUtils.hasText(apiKey)) {
            // Map to a friendly, non-technical message
            throw new AiFriendlyException("CONFIG_MISSING", "AI is not configured. Please contact support.");
        }
        try {
            var prompt = chatClient.prompt();
            if (StringUtils.hasText(system)) {
                prompt = prompt.system(system);
            }
            return prompt.user(user).call().content();
        } catch (Exception e) {
            String msg = normalizeMessage(e);
            throw new AiFriendlyException("AI_UNAVAILABLE", msg);
        }
    }

    private String normalizeMessage(Exception e) {
        String m = e.getMessage();
        if (m == null) return "The AI service is currently unavailable. Please try again.";
        String lower = m.toLowerCase();
        if (lower.contains("rate") && lower.contains("limit")) {
            return "The AI is receiving too many requests. Please wait a moment and try again.";
        }
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("connect")) {
            return "We could not reach the AI service. Check your connection and try again.";
        }
        if (lower.contains("unauthorized") || lower.contains("invalid api key") || lower.contains("401")) {
            return "AI credentials are invalid. Please contact support.";
        }
        // Default friendly message
        return "The assistant couldn’t respond right now. Please try again.";
    }
}
