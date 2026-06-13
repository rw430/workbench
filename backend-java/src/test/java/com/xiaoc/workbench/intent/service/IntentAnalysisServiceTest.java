package com.xiaoc.workbench.intent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IntentAnalysisServiceTest {
    private final IntentAnalysisService service = new IntentAnalysisService();

    @Test
    void recognizesCreditCardInstallmentGoldenPath() {
        IntentAnalysis analysis = service.analyze("请帮我设计一个银行信用卡分期活动配置与审批系统的研发方案，要求支持活动规则配置、审批流、风险校验、灰度发布和操作审计。");

        assertThat(analysis.mode()).isEqualTo("TASKS");
        assertThat(analysis.templateId()).isEqualTo("credit_card_installment_campaign_v1");
        assertThat(analysis.domain()).isEqualTo("banking_credit_card");
        assertThat(analysis.riskLevel()).isEqualTo("MEDIUM");
        assertThat(analysis.humanGateRequired()).isTrue();
        assertThat(analysis.candidateRoles()).containsExactly("PD", "DEV", "QA", "RISK", "PMO");
        assertThat(analysis.confidence()).isGreaterThanOrEqualTo(0.80);
    }

    @Test
    void fallsBackToGoldenTemplateForUnmatchedGoals() {
        IntentAnalysis analysis = service.analyze("整理一个通用研发任务");

        assertThat(analysis.templateId()).isEqualTo("credit_card_installment_campaign_v1");
        assertThat(analysis.confidence()).isLessThan(0.80);
        assertThat(analysis.candidateRoles()).containsExactly("PD", "DEV", "QA", "RISK", "PMO");
    }
}
