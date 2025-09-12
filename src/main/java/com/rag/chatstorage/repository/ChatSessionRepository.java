package com.rag.chatstorage.repository;

import com.rag.chatstorage.domain.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByUser_UserIdOrderByUpdatedAtDesc(String userId);
    List<ChatSession> findByUser_UserIdAndFavoriteOrderByUpdatedAtDesc(String userId, boolean favorite);
}
