package com.xiaoc.workbench.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "task_edges")
public class TaskEdge {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String runId;

    @Column(nullable = false, length = 128)
    private String sourceNodeId;

    @Column(nullable = false, length = 128)
    private String targetNodeId;

    protected TaskEdge() {
    }

    public TaskEdge(String id, String runId, String sourceNodeId, String targetNodeId) {
        this.id = id;
        this.runId = runId;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getSourceNodeId() { return sourceNodeId; }
    public String getTargetNodeId() { return targetNodeId; }
}
