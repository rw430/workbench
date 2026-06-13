package com.xiaoc.workbench.growth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lessons")
public class Lesson {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String reflectionId;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 32)
    private String confidence;

    @Column(nullable = false)
    private Instant createdAt;

    protected Lesson() {
    }

    public Lesson(String id, String reflectionId, String category, String content, String confidence) {
        this.id = id;
        this.reflectionId = reflectionId;
        this.category = category;
        this.content = content;
        this.confidence = confidence;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getReflectionId() { return reflectionId; }
    public String getCategory() { return category; }
    public String getContent() { return content; }
    public String getConfidence() { return confidence; }
    public Instant getCreatedAt() { return createdAt; }
}
