package com.xiaoc.workbench.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoc.workbench.agent.service.AgentRecommendationService;
import com.xiaoc.workbench.agent.service.BuiltinAgentSeeder;
import com.xiaoc.workbench.event.service.RuntimeEventService;
import com.xiaoc.workbench.governance.service.AuditLogService;
import com.xiaoc.workbench.growth.repository.LessonRepository;
import com.xiaoc.workbench.growth.repository.ReflectionRepository;
import com.xiaoc.workbench.growth.service.DeliveryGenerationService;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import com.xiaoc.workbench.orchestrator.repository.ArtifactRepository;
import com.xiaoc.workbench.orchestrator.template.DagTemplateLoader;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.service.ProjectApplicationService;
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
        DeliveryGenerationService.class,
        ProjectApplicationService.class,
        DeterministicTaskExecutor.class,
        RunnerService.class,
        RunnerDeliveryIntegrationTest.JacksonTestConfig.class
})
class RunnerDeliveryIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private BuiltinAgentSeeder seeder;

    @Autowired
    private ProjectApplicationService projectService;

    @Autowired
    private RunnerService runnerService;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private ReflectionRepository reflectionRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Test
    void approvalCompletionCreatesDeliveryDataAndReturnsItInProjectState() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign delivery");
        ProjectStateResponse waiting = runnerService.startRun(created.run().id());

        ProjectStateResponse completed = runnerService.approveGate(
                waiting.humanGate().id(),
                "scope confirmed",
                "local-user");

        assertThat(completed.run().status()).isEqualTo("completed");
        assertThat(completed.artifact()).contains("信用卡分期活动研发交付物");
        assertThat(completed.reflection()).contains("复盘");
        assertThat(completed.lessons()).extracting("category")
                .contains("scope_control", "risk_compliance", "delivery_quality");
        assertThat(completed.lessons()).anySatisfy(lesson -> assertThat(lesson.content())
                .contains("HumanGate 将 PRD 范围确认放在风险评审前"));
        assertThat(artifactRepository.findByRunId(created.run().id())).isPresent();
        assertThat(reflectionRepository.findByRunId(created.run().id())).isPresent();
        assertThat(lessonRepository.findAllByReflectionId(
                reflectionRepository.findByRunId(created.run().id()).orElseThrow().getId()))
                .hasSize(3);
    }

    @Test
    void deliveryGenerationIsIdempotentForCompletedRuns() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign idempotent delivery");
        ProjectStateResponse waiting = runnerService.startRun(created.run().id());
        ProjectStateResponse completed = runnerService.approveGate(
                waiting.humanGate().id(),
                "scope confirmed",
                "local-user");

        runnerService.startRun(completed.run().id());
        ProjectStateResponse loaded = projectService.getRunState(completed.run().id());

        assertThat(artifactRepository.findAll()).hasSize(1);
        assertThat(reflectionRepository.findAll()).hasSize(1);
        assertThat(lessonRepository.findAll()).hasSize(3);
        assertThat(loaded.lessons()).hasSize(3);
    }

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
