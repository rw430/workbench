package com.xiaoc.workbench.growth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "reflections")
public class Reflection {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String projectId;

    @Column(nullable = false, length = 64, unique = true)
    private String runId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    protected Reflection() {
    }

    public Reflection(String id, String projectId, String runId, String content) {
        this.id = id;
        this.projectId = projectId;
        this.runId = runId;
        this.content = content;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getRunId() { return runId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
