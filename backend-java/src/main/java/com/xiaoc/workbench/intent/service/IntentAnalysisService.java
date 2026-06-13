package com.xiaoc.workbench.intent.service;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IntentAnalysisService {
    private static final List<String> ROLES = List.of("PD", "DEV", "QA", "RISK", "PMO");
    private static final List<String> GOLDEN_KEYWORDS = List.of("银行", "信用卡", "分期", "活动", "审批", "风险", "灰度", "审计");

    public IntentAnalysis analyze(String goal) {
        String normalized = goal == null ? "" : goal.strip();
        long hits = GOLDEN_KEYWORDS.stream().filter(normalized::contains).count();
        double confidence = hits >= 4 ? 0.92 : 0.55;
        return new IntentAnalysis(
                "TASKS",
                "credit_card_installment_campaign_v1",
                "banking_credit_card",
                "MEDIUM",
                true,
                confidence,
                ROLES);
    }
}
