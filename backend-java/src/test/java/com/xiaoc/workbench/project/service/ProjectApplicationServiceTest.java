package com.xiaoc.workbench.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaoc.workbench.agent.api.AgentSummary;
import com.xiaoc.workbench.agent.service.AgentRecommendationService;
import com.xiaoc.workbench.agent.service.BuiltinAgentSeeder;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import com.xiaoc.workbench.orchestrator.template.DagTemplateLoader;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.api.TaskSummary;
import com.xiaoc.workbench.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({
        BuiltinAgentSeeder.class,
        IntentAnalysisService.class,
        AgentRecommendationService.class,
        DagTemplateLoader.class,
        ProjectApplicationService.class
})
public class ProjectApplicationServiceTest extends PostgresIntegrationTest {
    @Autowired
    private BuiltinAgentSeeder seeder;

    @Autowired
    private ProjectApplicationService service;

    @Test
    void createsProjectStateFromGoldenPathTemplate() {
        seeder.seedBuiltinAgents();

        ProjectStateResponse state = service.createProject("银行信用卡分期活动审批研发方案，包含风险校验、灰度发布和操作审计。");

        assertThat(state.project().mode()).isEqualTo("tasks");
        assertThat(state.project().status()).isEqualTo("created");
        assertThat(state.room().name()).isEqualTo("信用卡分期活动研发协同室");
        assertThat(state.run().templateId()).isEqualTo("credit_card_installment_campaign_v1");
        assertThat(state.run().status()).isEqualTo("created");
        assertThat(state.agents()).extracting(AgentSummary::role).containsExactly("PD", "DEV", "QA", "RISK", "PMO");
        assertThat(state.tasks()).hasSize(8);
        assertThat(state.tasks().get(0).nodeId()).isEqualTo("need_analysis");
        assertThat(state.tasks().get(0).status()).isEqualTo("ready");
        assertThat(state.tasks().get(1).kind()).isEqualTo("human_gate");
        assertThat(state.tasks().get(1).dependsOn()).containsExactly("need_analysis");
        assertThat(state.humanGate()).isNull();
        assertThat(state.artifact()).isNull();
        assertThat(state.reflection()).isNull();
    }

    @Test
    void reconstructsProjectStateByProjectId() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = service.createProject("银行信用卡分期活动审批研发方案");

        ProjectStateResponse loaded = service.getProject(created.project().id());

        assertThat(loaded.project().id()).isEqualTo(created.project().id());
        assertThat(loaded.tasks()).extracting(TaskSummary::nodeId)
                .containsExactlyElementsOf(created.tasks().stream().map(TaskSummary::nodeId).toList());
    }
}
