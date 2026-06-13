package com.xiaoc.workbench.project.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TaskSummary(
        String id,
        @JsonProperty("run_id") String runId,
        @JsonProperty("node_id") String nodeId,
        String name,
        String kind,
        String role,
        @JsonProperty("depends_on") List<String> dependsOn,
        String status,
        String output,
        String log
) {
}
