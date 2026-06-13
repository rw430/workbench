package com.xiaoc.workbench.project.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoomSummary(
        String id,
        @JsonProperty("project_id") String projectId,
        String name
) {
}
