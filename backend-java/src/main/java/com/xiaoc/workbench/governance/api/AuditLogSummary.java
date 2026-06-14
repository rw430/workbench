package com.xiaoc.workbench.governance.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public record AuditLogSummary(
        String id,
        @JsonProperty("actor_id") String actorId,
        String action,
        @JsonProperty("target_type") String targetType,
        @JsonProperty("target_id") String targetId,
        Map<String, Object> payload,
        @JsonProperty("created_at") Instant createdAt
) {
}
