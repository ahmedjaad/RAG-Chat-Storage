package com.rag.chatstorage.service.impl;

import com.rag.chatstorage.domain.ChatMessage;
import com.rag.chatstorage.service.AiService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class SimpleAiService implements AiService {
    private final ChatClient chatClient;

    @Autowired
        public SimpleAiService(ObjectProvider<ChatClient> chatClientProvider) {
        this.chatClient = chatClientProvider.getIfAvailable();
    }

    // Backwards-compatible constructor for tests/wiring expecting direct ChatClient
    public SimpleAiService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Retry(name = "ai", fallbackMethod = "fallback")
    @CircuitBreaker(name = "ai", fallbackMethod = "fallback")
    public String infer(String system, String user) {
        try {
            if (chatClient == null) {
                throw new AiFriendlyException(
                        "AI_NOT_CONFIGURED",
                        "No AI chat provider is configured. Enable a provider profile (e.g. openai, ollama) or set necessary environment variables.",
                        "Set SPRING_PROFILES_ACTIVE=openai and provide API keys, or use ollama profile."
                );
            }
            var prompt = chatClient.prompt();
            if (StringUtils.hasText(system)) {
                prompt = prompt.system(system);
            }
            return prompt.user(user).call().content();
        } catch (Exception e) {
            maybeHonorRetryAfter(e);
            String msg = normalizeMessage(e);
            String hint = extractHint(e);
            throw new AiFriendlyException("AI_UNAVAILABLE", msg, hint);
        }
    }

    @Retry(name = "ai", fallbackMethod = "fallbackWithHistory")
    @CircuitBreaker(name = "ai", fallbackMethod = "fallbackWithHistory")
    public String inferWithHistory(String user, String system, List<ChatMessage> history) {
        try {
            if (chatClient == null) {
                throw new AiFriendlyException(
                        "AI_NOT_CONFIGURED",
                        "No AI chat provider is configured. Enable a provider profile (e.g. openai, ollama) or set necessary environment variables.",
                        "Set SPRING_PROFILES_ACTIVE=openai and provide API keys, or use ollama profile."
                );
            }
            var prompt = chatClient.prompt();
            List<Message> msgs = new ArrayList<>();
            if (StringUtils.hasText(system)) {
                msgs.add(new SystemMessage(system));
            }
            // Limit to last 30 messages to keep prompt manageable
            int max = 30;
            int start = Math.max(0, history.size() - max);
            for (int i = start; i < history.size(); i++) {
                ChatMessage m = history.get(i);
                switch (m.getSender()) {
                    case USER -> msgs.add(new UserMessage(m.getContent()));
                    case ASSISTANT -> msgs.add(new AssistantMessage(m.getContent()));
                    case SYSTEM -> msgs.add(new SystemMessage(m.getContent()));
                }
            }
            return prompt.user(user).messages(msgs).call().content();
        } catch (Exception e) {
            maybeHonorRetryAfter(e);
            String msg = normalizeMessage(e);
            String hint = extractHint(e);
            throw new AiFriendlyException("AI_UNAVAILABLE", msg, hint);
        }
    }

    // Fallback for resilience4j annotations with history
    @SuppressWarnings("unused")
    private String fallbackWithHistory(String user, String system, List<ChatMessage> history, Throwable t) {
        String msg = normalizeMessage(t instanceof Exception e ? e : new Exception(t));
        String hint = extractHint(t instanceof Exception e ? e : new Exception(t));
        throw new AiFriendlyException("AI_UNAVAILABLE", msg, hint);
    }

    // If provider sends Retry-After on 429/5xx, wait that period before allowing Retry to re-execute
    private void maybeHonorRetryAfter(Exception e) {
        if (e instanceof WebClientResponseException wex) {
            String header = wex.getHeaders().getFirst("Retry-After");
            if (header == null) return;
            long waitMs = parseRetryAfter(header);
            if (waitMs > 0 && waitMs <= 30_000) { // cap to 30s
                try { Thread.sleep(waitMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
    }

    private long parseRetryAfter(String value) {
        try {
            value = value.trim();
            if (value.chars().allMatch(Character::isDigit)) {
                return Long.parseLong(value) * 1000L;
            }
            // RFC 1123 date
            ZonedDateTime when = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            long millis = Duration.between(ZonedDateTime.now(), when).toMillis();
            return Math.max(millis, 0);
        } catch (Exception ex) {
            return 0;
        }
    }

    // Fallback for resilience4j annotations
    @SuppressWarnings("unused")
    private String fallback(String system, String user, Throwable t) {
        String msg = normalizeMessage(t instanceof Exception e ? e : new Exception(t));
        String hint = extractHint(t instanceof Exception e ? e : new Exception(t));
        throw new AiFriendlyException("AI_UNAVAILABLE", msg, hint);
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
