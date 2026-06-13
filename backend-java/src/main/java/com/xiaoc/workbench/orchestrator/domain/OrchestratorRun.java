package com.xiaoc.workbench.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "orchestrator_runs")
public class OrchestratorRun {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String projectId;

    @Column(nullable = false, length = 128)
    private String templateId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected OrchestratorRun() {
    }

    public OrchestratorRun(String id, String projectId, String templateId, String status) {
        this.id = id;
        this.projectId = projectId;
        this.templateId = templateId;
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getTemplateId() { return templateId; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markRunning() {
        this.status = "RUNNING";
    }

    public void markWaitingHuman() {
        this.status = "WAITING_HUMAN";
    }

    public void markCompleted() {
        this.status = "COMPLETED";
    }

    public void markFailed() {
        this.status = "FAILED";
    }
}
