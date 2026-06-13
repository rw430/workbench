package com.xiaoc.workbench.project.api;

import jakarta.validation.constraints.NotBlank;

record CreateProjectRequest(@NotBlank String goal) {
}
