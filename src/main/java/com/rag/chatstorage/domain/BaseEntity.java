package com.rag.chatstorage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersistActions() {
        createdAt = Instant.now();
        onCreate();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant updatedAt) {
        this.createdAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    protected void onCreate() {
    }
}
