package com.xiaoc.workbench.agent.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record AgentSummary(
        String id,
        String name,
        String role,
        List<String> skills,
        BigDecimal score,
        @JsonProperty("recommendation_reason") String recommendationReason
) {
}
