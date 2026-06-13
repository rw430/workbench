package com.xiaoc.workbench.intent.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

record AnalyzeIntentRequest(@NotBlank String goal) {
}

record IntentAnalysisResponse(
        String mode,
        @JsonProperty("template_id") String templateId,
        String domain,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("human_gate_required") boolean humanGateRequired,
        double confidence,
        @JsonProperty("candidate_roles") List<String> candidateRoles
) {
}
