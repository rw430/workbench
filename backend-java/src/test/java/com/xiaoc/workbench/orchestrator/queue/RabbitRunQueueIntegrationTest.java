package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaoc.workbench.agent.service.AgentRecommendationService;
import com.xiaoc.workbench.agent.service.BuiltinAgentSeeder;
import com.xiaoc.workbench.event.service.RuntimeEventService;
import com.xiaoc.workbench.governance.service.AuditLogService;
import com.xiaoc.workbench.growth.service.DeliveryGenerationService;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import com.xiaoc.workbench.orchestrator.service.DeterministicTaskExecutor;
import com.xiaoc.workbench.orchestrator.service.RunnerService;
import com.xiaoc.workbench.orchestrator.template.DagTemplateLoader;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.service.ProjectApplicationService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "xiaoc.queue.mode=rabbit",
        "xiaoc.redis.enabled=true",
        "xiaoc.redis.fail-open=false",
        "xiaoc.rabbitmq.enabled=true",
        "xiaoc.run-lock.ttl-seconds=20",
        "xiaoc.rate-limit.run-start.max-requests=20",
        "xiaoc.rate-limit.run-start.window-seconds=60"
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
        RunnerService.class
})
class RabbitRunQueueIntegrationTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("xiaoc_test")
            .withUsername("xiaoc")
            .withPassword("xiaoc");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
            .withAdminUser("xiaoc")
            .withAdminPassword("xiaoc");

    static {
        POSTGRES.start();
        REDIS.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "xiaoc");
        registry.add("spring.rabbitmq.password", () -> "xiaoc");
    }

    @Autowired
    private BuiltinAgentSeeder seeder;

    @Autowired
    private ProjectApplicationService projectService;

    @Autowired
    private RunQueue runQueue;

    @Test
    void rabbitQueuePublishesAndWorkerAdvancesRun() throws Exception {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign with approval");

        ProjectStateResponse immediate = runQueue.enqueueStart(created.run().id());

        assertThat(immediate.run().id()).isEqualTo(created.run().id());
        ProjectStateResponse eventual = waitForRunStatus(created.run().id(), "waiting_human", Duration.ofSeconds(20));
        assertThat(eventual.humanGate()).isNotNull();
        assertThat(eventual.tasks()).anySatisfy(task -> {
            assertThat(task.nodeId()).isEqualTo("need_analysis");
            assertThat(task.status()).isEqualTo("completed");
        });
    }

    private ProjectStateResponse waitForRunStatus(String runId, String status, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        ProjectStateResponse state = projectService.getRunState(runId);
        while (System.nanoTime() < deadline) {
            state = projectService.getRunState(runId);
            if (status.equals(state.run().status())) {
                return state;
            }
            Thread.sleep(250);
        }
        return state;
    }
}