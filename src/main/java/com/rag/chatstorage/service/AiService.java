package com.rag.chatstorage.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiService {

    public static class AiFriendlyException extends RuntimeException {
        private final String code;
        private final String hint;
        public AiFriendlyException(String code, String message) {
            this(code, message, null);
        }
        public AiFriendlyException(String code, String message, String hint) {
            super(message);
            this.code = code;
            this.hint = hint;
        }
        public String getCode() { return code; }
        public String getHint() { return hint; }
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
            String hint = extractHint(e);
            throw new AiFriendlyException("AI_UNAVAILABLE", msg, hint);
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

    private String extractHint(Exception e) {
        String m = e.getMessage();
        if (m == null) return null;
        String lower = m.toLowerCase();
        // Extract simple status code hints if present
        if (lower.contains("429")) return "(429 rate limit)";
        if (lower.contains("401")) return "(401 unauthorized)";
        if (lower.contains("403")) return "(403 forbidden)";
        if (lower.contains("500")) return "(500 server error)";
        if (lower.contains("502")) return "(502 bad gateway)";
        if (lower.contains("503")) return "(503 unavailable)";
        if (lower.contains("timeout") || lower.contains("timed out")) return "(timeout)";
        if (lower.contains("connect") || lower.contains("connection")) return "(connection issue)";
        if (lower.contains("rate") && lower.contains("limit")) return "(rate limit)";
        if (lower.contains("invalid api key")) return "(invalid api key)";
        return null;
    }
}
