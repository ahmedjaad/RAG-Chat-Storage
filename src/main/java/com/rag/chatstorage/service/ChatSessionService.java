package com.rag.chatstorage.service;

import com.rag.chatstorage.domain.ChatMessage;
import com.rag.chatstorage.domain.ChatSession;
import java.util.List;
import org.springframework.data.domain.Page;

public interface ChatSessionService {

    ChatSession getSessionOrThrow(Long id);
    ChatSession createSession(String userId, String title);
    List<ChatSession> listSessions(String userId);
    List<ChatSession> listSessions(String userId, Boolean favorite);
    Page<ChatSession> pageSessions(String userId, Boolean favorite, String q, int page, int size);
    ChatSession rename(Long sessionId, String title);
    ChatSession favorite(Long sessionId, boolean favorite);
    void delete(Long sessionId);
    ChatMessage addMessage(Long sessionId, ChatMessage.Sender sender, String content, String context);
    Page<ChatMessage> getMessages(Long sessionId, int page, int size);
    List<ChatMessage> listAllMessagesOrdered(Long sessionId);
}
