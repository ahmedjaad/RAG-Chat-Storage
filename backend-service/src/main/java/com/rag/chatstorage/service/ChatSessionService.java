package com.rag.chatstorage.service;

import com.rag.chatstorage.domain.ChatMessage;
import com.rag.chatstorage.domain.ChatSession;
import com.rag.chatstorage.domain.User;
import com.rag.chatstorage.repository.ChatMessageRepository;
import com.rag.chatstorage.repository.ChatSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserService userService;

    public ChatSessionService(ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository, UserService userService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userService = userService;
    }

    public ChatSession getSessionOrThrow(Long id) {
        return sessionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    public ChatSession createSession(String userId, String title) {
        User u = userService.ensureUser(userId);
        ChatSession s = new ChatSession();
        s.setUser(u);
        s.setTitle(title);
        return sessionRepository.save(s);
    }

    public List<ChatSession> listSessions(String userId) {
        return sessionRepository.findByUser_UserIdOrderByUpdatedAtDesc(userId);
    }

    public List<ChatSession> listSessions(String userId, Boolean favorite) {
        if (favorite == null) return listSessions(userId);
        return sessionRepository.findByUser_UserIdAndFavoriteOrderByUpdatedAtDesc(userId, favorite);
    }

    public Page<ChatSession> pageSessions(String userId, Boolean favorite, String q, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (favorite == null && (q == null || q.isBlank())) {
            return sessionRepository.findByUser_UserIdOrderByUpdatedAtDesc(userId, pageable);
        }
        if (favorite == null) {
            return sessionRepository.findByUser_UserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(userId, q, pageable);
        }
        if (q == null || q.isBlank()) {
            return sessionRepository.findByUser_UserIdAndFavoriteOrderByUpdatedAtDesc(userId, favorite, pageable);
        }
        return sessionRepository.findByUser_UserIdAndFavoriteAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(userId, favorite, q, pageable);
    }

    public ChatSession rename(Long sessionId, String title) {
        ChatSession s = sessionRepository.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("Session not found"));
        s.setTitle(title);
        return sessionRepository.save(s);
    }

    public ChatSession favorite(Long sessionId, boolean favorite) {
        ChatSession s = sessionRepository.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("Session not found"));
        s.setFavorite(favorite);
        return sessionRepository.save(s);
    }

    public void delete(Long sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    public ChatMessage addMessage(Long sessionId, ChatMessage.Sender sender, String content, String context) {
        ChatSession s = sessionRepository.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("Session not found"));
        ChatMessage m = new ChatMessage();
        m.setSession(s);
        m.setSender(sender);
        m.setContent(content);
        // Cap context length defensively to avoid oversized storage even if validation bypassed
        if (context != null && context.length() > 20000) {
            context = context.substring(0, 20000);
        }
        m.setContext(context);
        return messageRepository.save(m);
    }

    public Page<ChatMessage> getMessages(Long sessionId, int page, int size) {
        return messageRepository.findBySession_Id(sessionId, PageRequest.of(page, size));
    }
}
