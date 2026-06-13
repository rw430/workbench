package com.xiaoc.workbench.project.api;

public record ProjectSummary(
        String id,
        String goal,
        String mode,
        String status
) {
}
