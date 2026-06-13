package com.xiaoc.workbench.governance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String actorId;

    @Column(nullable = false, length = 96)
    private String action;

    @Column(nullable = false, length = 64)
    private String targetType;

    @Column(nullable = false, length = 64)
    private String targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(
        String id,
        String actorId,
        String action,
        String targetType,
        String targetId,
        Map<String, Object> payload
    ) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.payload = Map.copyOf(payload);
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
