package com.xiaoc.workbench.project.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RunSummary(
        String id,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("template_id") String templateId,
        String status
) {
}
