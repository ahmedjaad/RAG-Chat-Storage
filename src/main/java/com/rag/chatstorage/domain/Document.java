package com.rag.chatstorage.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "documents")
public class Document extends BaseEntity {

    @Column(nullable = false)
    private String userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(columnDefinition = "TEXT")
    private String metadata; // simple JSON string

    @Column(columnDefinition = "TEXT")
    private String vector; // comma-separated floats for minimal storage

    @Column(nullable = false)
    private Integer dimensions;

    public Document() {}

    public Document(String userId, String text, String metadata, String vector, Integer dimensions) {
        this.userId = userId;
        this.text = text;
        this.metadata = metadata;
        this.vector = vector;
        this.dimensions = dimensions;
    }

    // getters and setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getVector() { return vector; }
    public void setVector(String vector) { this.vector = vector; }
    public Integer getDimensions() { return dimensions; }
    public void setDimensions(Integer dimensions) { this.dimensions = dimensions; }
}
