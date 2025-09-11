package com.rag.chatstorage.service;

import com.rag.chatstorage.domain.ChatMessage;
import com.rag.chatstorage.domain.ChatSession;
import com.rag.chatstorage.repository.ChatMessageRepository;
import com.rag.chatstorage.repository.ChatSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    public ChatSessionService(ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    public ChatSession createSession(String userId, String title) {
        ChatSession s = new ChatSession();
        s.setUserId(userId);
        s.setTitle(title);
        return sessionRepository.save(s);
    }

    public List<ChatSession> listSessions(String userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
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
        m.setContext(context);
        return messageRepository.save(m);
    }

    public Page<ChatMessage> getMessages(Long sessionId, int page, int size) {
        return messageRepository.findBySession_Id(sessionId, PageRequest.of(page, size));
    }
}
