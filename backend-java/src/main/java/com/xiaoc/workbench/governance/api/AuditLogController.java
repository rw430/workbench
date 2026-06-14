package com.xiaoc.workbench.governance.api;

import com.xiaoc.workbench.governance.service.AuditLogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AuditLogSummary> list(
            @RequestParam(value = "actor_id", required = false) String actorId,
            @RequestParam(value = "target_type", required = false) String targetType,
            @RequestParam(value = "target_id", required = false) String targetId
    ) {
        if (hasText(actorId)) {
            return auditLogService.findByActor(actorId.strip());
        }
        if (hasText(targetType) && hasText(targetId)) {
            return auditLogService.findByTarget(targetType.strip(), targetId.strip());
        }
        throw new IllegalArgumentException("actor_id or target_type and target_id is required");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
