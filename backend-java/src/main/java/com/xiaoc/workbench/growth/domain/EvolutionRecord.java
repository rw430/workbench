package com.xiaoc.workbench.growth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "evolution_records")
public class EvolutionRecord {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String agentId;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false)
    private Instant createdAt;

    protected EvolutionRecord() {
    }

    public EvolutionRecord(String id, String agentId, String summary) {
        this.id = id;
        this.agentId = agentId;
        this.summary = summary;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getAgentId() { return agentId; }
    public String getSummary() { return summary; }
    public Instant getCreatedAt() { return createdAt; }
}
