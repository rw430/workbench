package com.xiaoc.workbench.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "human_gates")
public class HumanGate {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String runId;

    @Column(nullable = false, length = 64)
    private String taskId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(columnDefinition = "text")
    private String decisionReason;

    @Column(length = 64)
    private String decidedBy;

    private Instant decidedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected HumanGate() {
    }

    public HumanGate(String id, String runId, String taskId, String status, String prompt) {
        this.id = id;
        this.runId = runId;
        this.taskId = taskId;
        this.status = status;
        this.prompt = prompt;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getTaskId() { return taskId; }
    public String getStatus() { return status; }
    public String getPrompt() { return prompt; }
    public String getDecisionReason() { return decisionReason; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
