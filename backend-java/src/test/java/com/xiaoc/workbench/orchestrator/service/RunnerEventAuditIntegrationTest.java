package com.xiaoc.workbench.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoc.workbench.agent.service.AgentRecommendationService;
import com.xiaoc.workbench.agent.service.BuiltinAgentSeeder;
import com.xiaoc.workbench.event.service.RuntimeEventService;
import com.xiaoc.workbench.governance.domain.AuditLog;
import com.xiaoc.workbench.governance.repository.AuditLogRepository;
import com.xiaoc.workbench.governance.service.AuditLogService;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import com.xiaoc.workbench.orchestrator.domain.RuntimeEvent;
import com.xiaoc.workbench.orchestrator.repository.RuntimeEventRepository;
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
        ProjectApplicationService.class,
        DeterministicTaskExecutor.class,
        RunnerService.class,
        RunnerEventAuditIntegrationTest.JacksonTestConfig.class
})
class RunnerEventAuditIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private BuiltinAgentSeeder seeder;

    @Autowired
    private ProjectApplicationService projectService;

    @Autowired
    private RunnerService runnerService;

    @Autowired
    private RuntimeEventRepository runtimeEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void recordsEventsAndAuditForStartAndApproval() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign approval");
        ProjectStateResponse waiting = runnerService.startRun(created.run().id());

        runnerService.approveGate(waiting.humanGate().id(), "scope confirmed", "local-user");

        assertThat(runtimeEventRepository.findAllByRunIdOrderByCreatedAtAscIdAsc(created.run().id()))
                .extracting(RuntimeEvent::getEventType)
                .contains(
                        "project.created",
                        "run.started",
                        "task.completed",
                        "human_gate.waiting",
                        "human_gate.approved",
                        "run.completed");
        assertThat(auditLogRepository.findAllByActorIdOrderByCreatedAtDescIdDesc("local-user"))
                .extracting(AuditLog::getAction)
                .contains("PROJECT_CREATE", "RUN_START", "HUMAN_GATE_APPROVE", "RUN_COMPLETED");
    }

    @Test
    void recordsEventsAndAuditForGateRejection() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign rejection");
        ProjectStateResponse waiting = runnerService.startRun(created.run().id());

        runnerService.rejectGate(waiting.humanGate().id(), "scope too broad", "local-user");

        assertThat(runtimeEventRepository.findAllByRunIdOrderByCreatedAtAscIdAsc(created.run().id()))
                .extracting(RuntimeEvent::getEventType)
                .contains("human_gate.rejected", "run.failed");
        assertThat(auditLogRepository.findAllByActorIdOrderByCreatedAtDescIdDesc("local-user"))
                .extracting(AuditLog::getAction)
                .contains("HUMAN_GATE_REJECT", "RUN_FAILED");
    }

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
