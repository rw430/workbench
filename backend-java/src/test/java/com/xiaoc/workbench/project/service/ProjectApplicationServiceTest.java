package com.xiaoc.workbench.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoc.workbench.agent.api.AgentSummary;
import com.xiaoc.workbench.agent.service.AgentRecommendationService;
import com.xiaoc.workbench.agent.service.BuiltinAgentSeeder;
import com.xiaoc.workbench.event.service.RuntimeEventService;
import com.xiaoc.workbench.governance.service.AuditLogService;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import com.xiaoc.workbench.orchestrator.domain.HumanGate;
import com.xiaoc.workbench.orchestrator.repository.HumanGateRepository;
import com.xiaoc.workbench.orchestrator.template.DagTemplateLoader;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.api.TaskSummary;
import com.xiaoc.workbench.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Import({
        BuiltinAgentSeeder.class,
        IntentAnalysisService.class,
        AgentRecommendationService.class,
        DagTemplateLoader.class,
        RuntimeEventService.class,
        AuditLogService.class,
        ProjectApplicationService.class,
        ProjectApplicationServiceTest.JacksonTestConfig.class
})
public class ProjectApplicationServiceTest extends PostgresIntegrationTest {
    @Autowired
    private BuiltinAgentSeeder seeder;

    @Autowired
    private ProjectApplicationService service;

    @Autowired
    private HumanGateRepository humanGateRepository;

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
        assertThat(state.lessons()).isEmpty();
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
    @Test
    void returnsWaitingHumanGateInProjectState() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = service.createProject("credit card installment campaign approval workflow");
        TaskSummary gateTask = created.tasks().stream()
                .filter(task -> task.kind().equals("human_gate"))
                .findFirst()
                .orElseThrow();
        humanGateRepository.save(new HumanGate(
                "gate-project-state",
                created.run().id(),
                gateTask.id(),
                "WAITING",
                "Confirm PRD scope before risk review."));

        ProjectStateResponse loaded = service.getProject(created.project().id());

        assertThat(loaded.humanGate()).isNotNull();
        assertThat(loaded.humanGate().id()).isEqualTo("gate-project-state");
        assertThat(loaded.humanGate().status()).isEqualTo("waiting");
        assertThat(loaded.humanGate().prompt()).contains("Confirm PRD scope");
    }

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
