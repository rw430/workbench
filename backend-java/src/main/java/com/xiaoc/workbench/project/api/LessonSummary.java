package com.xiaoc.workbench.project.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record LessonSummary(
        String id,
        @JsonProperty("reflection_id") String reflectionId,
        String category,
        String content,
        String confidence,
        @JsonProperty("created_at") Instant createdAt
) {
}
