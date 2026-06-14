package com.xiaoc.workbench.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoc.workbench.event.api.RuntimeEventEnvelope;
import com.xiaoc.workbench.orchestrator.domain.OrchestratorRun;
import com.xiaoc.workbench.orchestrator.repository.OrchestratorRunRepository;
import com.xiaoc.workbench.orchestrator.repository.RuntimeEventRepository;
import com.xiaoc.workbench.project.domain.Project;
import com.xiaoc.workbench.project.repository.ProjectRepository;
import com.xiaoc.workbench.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Import({RuntimeEventService.class, RuntimeEventServiceTest.JacksonTestConfig.class})
class RuntimeEventServiceTest extends PostgresIntegrationTest {
    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private OrchestratorRunRepository runRepository;

    @Autowired
    private RuntimeEventRepository repository;

    @Autowired
    private RuntimeEventService service;

    private OrchestratorRun run;

    @BeforeEach
    void setUp() {
        Project project = projectRepository.save(
                new Project("project-event-service", "event service check", "TASKS", "RUNNING"));
        run = runRepository.save(new OrchestratorRun(
                "run-event-service",
                project.getId(),
                "credit_card_installment_campaign_v1",
                "RUNNING"));
    }

    @Test
    void appendsJsonPayloadAndReplaysEventsAfterId() {
        RuntimeEventEnvelope first = service.append(
                run.getId(),
                "run.started",
                Map.of("status", "running"));
        RuntimeEventEnvelope second = service.append(
                run.getId(),
                "task.completed",
                Map.of("task_id", "task-1", "node_id", "need_analysis"));

        assertThat(service.replay(run.getId(), null))
                .extracting(RuntimeEventEnvelope::eventType)
                .containsExactly("run.started", "task.completed");
        assertThat(service.replay(run.getId(), first.id()))
                .extracting(RuntimeEventEnvelope::id)
                .containsExactly(second.id());
        assertThat(repository.findById(second.id()).orElseThrow().getPayload())
                .contains("\"task_id\"");
    }

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
