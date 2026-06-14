package com.xiaoc.workbench.governance.repository;

import com.xiaoc.workbench.governance.domain.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findAllByActorIdOrderByCreatedAtDescIdDesc(String actorId);

    List<AuditLog> findAllByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(String targetType, String targetId);
}
