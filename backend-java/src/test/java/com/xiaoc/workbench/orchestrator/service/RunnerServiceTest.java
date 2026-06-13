package com.xiaoc.workbench.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xiaoc.workbench.common.web.InvalidStateException;
import com.xiaoc.workbench.agent.service.AgentRecommendationService;
import com.xiaoc.workbench.agent.service.BuiltinAgentSeeder;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import com.xiaoc.workbench.orchestrator.template.DagTemplateLoader;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.api.TaskSummary;
import com.xiaoc.workbench.project.service.ProjectApplicationService;
import com.xiaoc.workbench.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({
        BuiltinAgentSeeder.class,
        IntentAnalysisService.class,
        AgentRecommendationService.class,
        DagTemplateLoader.class,
        ProjectApplicationService.class,
        DeterministicTaskExecutor.class,
        RunnerService.class
})
class RunnerServiceTest extends PostgresIntegrationTest {
    @Autowired
    private BuiltinAgentSeeder seeder;

    @Autowired
    private ProjectApplicationService projectService;

    @Autowired
    private RunnerService runnerService;

    @Test
    void startRunStopsAtHumanGate() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign with approval");

        ProjectStateResponse state = runnerService.startRun(created.run().id());

        assertThat(state.project().status()).isEqualTo("waiting_human");
        assertThat(state.run().status()).isEqualTo("waiting_human");
        assertThat(state.tasks()).filteredOn(task -> task.nodeId().equals("need_analysis"))
                .singleElement()
                .extracting(TaskSummary::status)
                .isEqualTo("completed");
        assertThat(state.tasks()).filteredOn(task -> task.nodeId().equals("human_gate_prd"))
                .singleElement()
                .extracting(TaskSummary::status)
                .isEqualTo("waiting_human");
        assertThat(state.humanGate()).isNotNull();
        assertThat(state.humanGate().status()).isEqualTo("waiting");
    }

    @Test
    void startRunIsIdempotentWhenAlreadyWaiting() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign with approval");
        ProjectStateResponse waiting = runnerService.startRun(created.run().id());

        ProjectStateResponse second = runnerService.startRun(created.run().id());

        assertThat(second.humanGate().id()).isEqualTo(waiting.humanGate().id());
        assertThat(second.tasks()).filteredOn(task -> task.nodeId().equals("need_analysis"))
                .singleElement()
                .extracting(TaskSummary::status)
                .isEqualTo("completed");
    }

    @Test
    void approveGateResumesAndCompletesRun() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign with approval");
        ProjectStateResponse waiting = runnerService.startRun(created.run().id());

        ProjectStateResponse completed = runnerService.approveGate(
                waiting.humanGate().id(),
                "scope confirmed",
                "local-user");

        assertThat(completed.project().status()).isEqualTo("completed");
        assertThat(completed.run().status()).isEqualTo("completed");
        assertThat(completed.humanGate().status()).isEqualTo("approved");
        assertThat(completed.tasks()).extracting(TaskSummary::status).containsOnly("completed");
        assertThat(completed.tasks()).filteredOn(task -> task.nodeId().equals("risk_compliance_review"))
                .singleElement()
                .extracting(TaskSummary::output)
                .asString()
                .contains("risk");
    }

    @Test
    void rejectGateFailsRunAndLeavesDownstreamPending() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign with approval");
        ProjectStateResponse waiting = runnerService.startRun(created.run().id());

        ProjectStateResponse failed = runnerService.rejectGate(
                waiting.humanGate().id(),
                "scope too broad",
                "local-user");

        assertThat(failed.project().status()).isEqualTo("failed");
        assertThat(failed.run().status()).isEqualTo("failed");
        assertThat(failed.humanGate().status()).isEqualTo("rejected");
        assertThat(failed.tasks()).filteredOn(task -> task.nodeId().equals("human_gate_prd"))
                .singleElement()
                .extracting(TaskSummary::status)
                .isEqualTo("failed");
        assertThat(failed.tasks()).filteredOn(task -> task.nodeId().equals("risk_compliance_review"))
                .singleElement()
                .extracting(TaskSummary::status)
                .isEqualTo("pending");
    }

    @Test
    void approvingRejectedGateThrowsInvalidState() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign with approval");
        ProjectStateResponse waiting = runnerService.startRun(created.run().id());
        runnerService.rejectGate(waiting.humanGate().id(), "scope too broad", "local-user");

        assertThatThrownBy(() -> runnerService.approveGate(
                waiting.humanGate().id(),
                "scope confirmed",
                "local-user"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("already rejected");
    }

    @Test
    void rejectingApprovedGateThrowsInvalidState() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign with approval");
        ProjectStateResponse waiting = runnerService.startRun(created.run().id());
        runnerService.approveGate(waiting.humanGate().id(), "scope confirmed", "local-user");

        assertThatThrownBy(() -> runnerService.rejectGate(
                waiting.humanGate().id(),
                "scope too broad",
                "local-user"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("already approved");
    }
}
