package com.xiaoc.workbench.orchestrator.queue;

import java.time.Instant;

public record RunStartMessage(
        String runId,
        String requestedBy,
        Instant requestedAt,
        String traceId
) {
}