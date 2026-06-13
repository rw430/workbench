package com.xiaoc.workbench.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "orchestrator_tasks")
public class OrchestratorTask {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String runId;

    @Column(nullable = false, length = 128)
    private String nodeId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String kind;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, columnDefinition = "text")
    private String output;

    @Column(nullable = false, columnDefinition = "text")
    private String log;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected OrchestratorTask() {
    }

    public OrchestratorTask(
        String id,
        String runId,
        String nodeId,
        String name,
        String kind,
        String role,
        String status,
        String output,
        String log,
        int sortOrder
    ) {
        this.id = id;
        this.runId = runId;
        this.nodeId = nodeId;
        this.name = name;
        this.kind = kind;
        this.role = role;
        this.status = status;
        this.output = output;
        this.log = log;
        this.sortOrder = sortOrder;
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
    public String getRunId() { return runId; }
    public String getNodeId() { return nodeId; }
    public String getName() { return name; }
    public String getKind() { return kind; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getOutput() { return output; }
    public String getLog() { return log; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
