package com.rag.chatstorage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User extends BaseEntity {

    @Column(name = "user_id", nullable = false, length = 128, unique = true)
    private String userId;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}