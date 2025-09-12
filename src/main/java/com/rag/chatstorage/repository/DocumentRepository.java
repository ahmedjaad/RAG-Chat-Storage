package com.rag.chatstorage.repository;

import com.rag.chatstorage.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserId(String userId);
}
