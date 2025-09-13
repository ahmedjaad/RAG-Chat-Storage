package com.rag.chatstorage.web.dto;

import com.rag.chatstorage.domain.ChatMessage;
import com.rag.chatstorage.domain.ChatSession;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public class SessionDtos {

    public record CreateSessionRequest(
            @NotBlank String userId,
            String title
    ) {}

    public record RenameSessionRequest(@NotBlank String title) {}

    public record FavoriteRequest(@NotNull Boolean favorite) {}

    public record AddMessageRequest(
            @NotNull ChatMessage.Sender sender,
            @NotBlank @Size(max = 10000) String content,
            @Size(max = 20000) String context
    ) {}

    public record PageRequestQuery(
            @Min(0) int page,
            @Min(1) int size
    ) {}

    public record SessionResponse(
            Long id,
            String userId,
            String title,
            boolean favorite,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static SessionResponse from(ChatSession s) {
            return new SessionResponse(s.getId(), s.getUser().getUserId(), s.getTitle(), s.isFavorite(), s.getCreatedAt(), s.getUpdatedAt());
        }
    }

    public record MessageResponse(
            Long id,
            Long sessionId,
            ChatMessage.Sender sender,
            String content,
            String context,
            OffsetDateTime createdAt
    ) {
        public static MessageResponse from(ChatMessage m) {
            return new MessageResponse(m.getId(), m.getSession().getId(), m.getSender(), m.getContent(), m.getContext(), m.getCreatedAt());
        }
    }

    public record PagedMessages(
            List<MessageResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record PagedSessions(
            java.util.List<SessionResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
