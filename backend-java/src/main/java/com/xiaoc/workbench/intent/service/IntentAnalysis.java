package com.xiaoc.workbench.intent.service;

import java.util.List;

public record IntentAnalysis(
        String mode,
        String templateId,
        String domain,
        String riskLevel,
        boolean humanGateRequired,
        double confidence,
        List<String> candidateRoles
) {
}
