package com.xiaoc.workbench.project.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HumanGateSummary(
        String id,
        @JsonProperty("run_id") String runId,
        @JsonProperty("task_id") String taskId,
        String status,
        String prompt
) {
}
