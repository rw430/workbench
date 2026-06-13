package com.xiaoc.workbench.orchestrator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

record HumanGateDecisionRequest(
        @NotBlank String reason,
        @JsonProperty("decided_by") String decidedBy
) {
    String effectiveDecidedBy() {
        return decidedBy == null || decidedBy.isBlank() ? "local-user" : decidedBy.strip();
    }
}
