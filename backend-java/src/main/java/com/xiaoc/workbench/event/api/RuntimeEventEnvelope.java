package com.xiaoc.workbench.event.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public record RuntimeEventEnvelope(
        String id,
        @JsonProperty("run_id") String runId,
        @JsonProperty("event_type") String eventType,
        Map<String, Object> payload,
        @JsonProperty("created_at") Instant createdAt
) {
}
