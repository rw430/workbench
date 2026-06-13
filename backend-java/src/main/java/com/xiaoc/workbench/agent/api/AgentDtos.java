package com.xiaoc.workbench.agent.api;

import jakarta.validation.constraints.NotBlank;

record RecommendAgentsRequest(@NotBlank String goal) {
}
