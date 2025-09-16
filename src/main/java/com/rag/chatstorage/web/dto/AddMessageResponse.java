package com.rag.chatstorage.web.dto;

import com.rag.chatstorage.domain.ChatMessage;
import java.time.Instant;

public class AddMessageResponse {

    private MessageResponse userMessage;
    private MessageResponse aiMessage; // can be null
    private AiError aiError; // can be null if AI succeeds

    public record MessageResponse(
            Long id,
            Long sessionId,
            ChatMessage.Sender sender,
            String content,
            String context,
            Instant createdAt
    ) {
        public static MessageResponse from(ChatMessage m) {
            return new MessageResponse(
                    m.getId(),
                    m.getSession().getId(),
                    m.getSender(),
                    m.getContent(),
                    m.getContext(),
                    m.getCreatedAt()
            );
        }
    }

    public record AiError(
            String message,
            String code,
            String hint
    ) {}

    // getters and setters
    public MessageResponse getUserMessage() { return userMessage; }
    public void setUserMessage(MessageResponse userMessage) { this.userMessage = userMessage; }

    public MessageResponse getAiMessage() { return aiMessage; }
    public void setAiMessage(MessageResponse aiMessage) { this.aiMessage = aiMessage; }

    public AiError getAiError() { return aiError; }
    public void setAiError(AiError aiError) { this.aiError = aiError; }
}
