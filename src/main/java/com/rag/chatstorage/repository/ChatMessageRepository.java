package com.rag.chatstorage.repository;

import com.rag.chatstorage.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Page<ChatMessage> findBySession_Id(Long sessionId, Pageable pageable);
    List<ChatMessage> findBySession_IdOrderByCreatedAtAsc(Long sessionId);
}
