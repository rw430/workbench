package com.xiaoc.workbench.governance.service;

import com.xiaoc.workbench.governance.api.AuditLogSummary;
import com.xiaoc.workbench.governance.domain.AuditLog;
import com.xiaoc.workbench.governance.repository.AuditLogRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditLogSummary record(
            String actorId,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> payload
    ) {
        AuditLog auditLog = repository.save(new AuditLog(
                id("audit"),
                normalize(actorId, "local-user"),
                action,
                targetType,
                targetId,
                payload == null ? Map.of() : payload));
        return toSummary(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AuditLogSummary> findByActor(String actorId) {
        return repository.findAllByActorIdOrderByCreatedAtDescIdDesc(actorId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogSummary> findByTarget(String targetType, String targetId) {
        return repository.findAllByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(targetType, targetId).stream()
                .map(this::toSummary)
                .toList();
    }

    private AuditLogSummary toSummary(AuditLog auditLog) {
        return new AuditLogSummary(
                auditLog.getId(),
                auditLog.getActorId(),
                auditLog.getAction(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getPayload(),
                auditLog.getCreatedAt());
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
