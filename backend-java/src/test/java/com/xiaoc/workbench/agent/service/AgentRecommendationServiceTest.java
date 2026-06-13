package com.xiaoc.workbench.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaoc.workbench.agent.api.AgentSummary;
import com.xiaoc.workbench.intent.service.IntentAnalysis;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import com.xiaoc.workbench.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({BuiltinAgentSeeder.class, AgentRecommendationService.class, IntentAnalysisService.class})
public class AgentRecommendationServiceTest extends PostgresIntegrationTest {
    @Autowired
    private BuiltinAgentSeeder seeder;

    @Autowired
    private IntentAnalysisService intentService;

    @Autowired
    private AgentRecommendationService recommendationService;

    @Test
    void recommendsEnabledAgentsInIntentRoleOrder() {
        seeder.seedBuiltinAgents();
        IntentAnalysis analysis = intentService.analyze("银行信用卡分期活动审批研发方案，包含风险校验和操作审计。");

        List<AgentSummary> agents = recommendationService.recommend(analysis);

        assertThat(agents).extracting(AgentSummary::role).containsExactly("PD", "DEV", "QA", "RISK", "PMO");
        assertThat(agents.get(0).skills()).contains("PRD", "需求分析");
        assertThat(agents.get(3).recommendationReason()).contains("风险边界");
    }
}
