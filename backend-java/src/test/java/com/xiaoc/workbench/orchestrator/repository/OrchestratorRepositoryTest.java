package com.xiaoc.workbench.orchestrator.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xiaoc.workbench.orchestrator.domain.Artifact;
import com.xiaoc.workbench.orchestrator.domain.HumanGate;
import com.xiaoc.workbench.orchestrator.domain.OrchestratorRun;
import com.xiaoc.workbench.orchestrator.domain.OrchestratorTask;
import com.xiaoc.workbench.orchestrator.domain.RuntimeEvent;
import com.xiaoc.workbench.orchestrator.domain.TaskEdge;
import com.xiaoc.workbench.project.domain.Project;
import com.xiaoc.workbench.project.domain.Room;
import com.xiaoc.workbench.project.repository.ProjectRepository;
import com.xiaoc.workbench.project.repository.RoomRepository;
import com.xiaoc.workbench.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class OrchestratorRepositoryTest extends PostgresIntegrationTest {
    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private OrchestratorRunRepository runRepository;

    @Autowired
    private OrchestratorTaskRepository taskRepository;

    @Autowired
    private TaskEdgeRepository edgeRepository;

    @Autowired
    private HumanGateRepository humanGateRepository;

    @Autowired
    private RuntimeEventRepository runtimeEventRepository;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Test
    void persistsProjectRunTasksGateAndEvents() {
        Project project = projectRepository.save(
            new Project("project-001", "信用卡分期活动配置与审批系统研发方案", "TASKS", "WAITING_HUMAN")
        );
        roomRepository.save(new Room("room-001", project.getId(), "信用卡分期活动研发协同室"));
        OrchestratorRun run = runRepository.save(
            new OrchestratorRun("run-001", project.getId(), "credit_card_installment_campaign_v1", "WAITING_HUMAN")
        );
        OrchestratorTask first = taskRepository.save(
            new OrchestratorTask(
                "task-001",
                run.getId(),
                "need_analysis",
                "需求分析",
                "LLM_PRD_DRAFT",
                "PD",
                "COMPLETED",
                "PRD 草稿",
                "llm/prd_draft executed",
                0
            )
        );
        OrchestratorTask gateTask = taskRepository.save(
            new OrchestratorTask(
                "task-002",
                run.getId(),
                "human_gate_prd",
                "PRD 确认",
                "HUMAN_GATE",
                "USER",
                "WAITING_HUMAN",
                "",
                "waiting",
                1
            )
        );
        edgeRepository.save(new TaskEdge("edge-001", run.getId(), first.getNodeId(), gateTask.getNodeId()));
        HumanGate gate = humanGateRepository.save(
            new HumanGate("gate-001", run.getId(), gateTask.getId(), "WAITING", "请确认 PRD 草稿是否可以进入评审。")
        );
        runtimeEventRepository.save(
            new RuntimeEvent("event-001", run.getId(), "human_gate.waiting", "{\"gate_id\":\"gate-001\"}")
        );
        artifactRepository.save(new Artifact("artifact-001", project.getId(), run.getId(), "# PRD"));

        assertThat(projectRepository.existsById(project.getId())).isTrue();
        assertThat(runRepository.findById(run.getId()).orElseThrow().getTemplateId())
            .isEqualTo("credit_card_installment_campaign_v1");
        assertThat(roomRepository.findByProjectId(project.getId()).orElseThrow().getName()).contains("协同室");
        assertThat(taskRepository.findAllByRunIdOrderBySortOrderAsc(run.getId()))
            .extracting(OrchestratorTask::getNodeId)
            .containsExactly("need_analysis", "human_gate_prd");
        assertThat(edgeRepository.findAllByRunId(run.getId())).hasSize(1);
        assertThat(humanGateRepository.findById(gate.getId()).orElseThrow().getStatus()).isEqualTo("WAITING");
        assertThat(runtimeEventRepository.findAllByRunIdOrderByCreatedAtAscIdAsc(run.getId()))
            .extracting(RuntimeEvent::getEventType)
            .containsExactly("human_gate.waiting");
        assertThat(artifactRepository.findByRunId(run.getId()).orElseThrow().getContent()).isEqualTo("# PRD");
    }

    @Test
    void rejectsEdgeWhenEndpointNodeDoesNotExistInRun() {
        Project project = projectRepository.save(
            new Project("project-edge-integrity", "验证 DAG 边端点", "TASKS", "RUNNING")
        );
        OrchestratorRun run = runRepository.save(
            new OrchestratorRun("run-edge-integrity", project.getId(), "credit_card_installment_campaign_v1", "RUNNING")
        );
        taskRepository.save(
            new OrchestratorTask(
                "task-edge-source",
                run.getId(),
                "need_analysis",
                "需求分析",
                "LLM_PRD_DRAFT",
                "PD",
                "COMPLETED",
                "",
                "",
                0
            )
        );

        assertThatThrownBy(() -> edgeRepository.saveAndFlush(
            new TaskEdge("edge-invalid-target", run.getId(), "need_analysis", "missing_node")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsHumanGateWhenTaskBelongsToDifferentRun() {
        Project project = projectRepository.save(
            new Project("project-gate-integrity", "验证 HumanGate 归属", "TASKS", "WAITING_HUMAN")
        );
        OrchestratorRun owningRun = runRepository.save(
            new OrchestratorRun("run-gate-owner", project.getId(), "credit_card_installment_campaign_v1", "WAITING_HUMAN")
        );
        OrchestratorRun otherRun = runRepository.save(
            new OrchestratorRun("run-gate-other", project.getId(), "credit_card_installment_campaign_v1", "WAITING_HUMAN")
        );
        OrchestratorTask task = taskRepository.save(
            new OrchestratorTask(
                "task-gate-owner",
                owningRun.getId(),
                "human_gate_prd",
                "PRD 确认",
                "HUMAN_GATE",
                "USER",
                "WAITING_HUMAN",
                "",
                "",
                0
            )
        );

        assertThatThrownBy(() -> humanGateRepository.saveAndFlush(
            new HumanGate("gate-mismatched-run", otherRun.getId(), task.getId(), "WAITING", "请确认 PRD。")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsArtifactWhenProjectDoesNotOwnRun() {
        Project owner = projectRepository.save(
            new Project("project-artifact-owner", "真实项目", "TASKS", "COMPLETED")
        );
        Project other = projectRepository.save(
            new Project("project-artifact-other", "错误项目", "TASKS", "COMPLETED")
        );
        OrchestratorRun run = runRepository.save(
            new OrchestratorRun("run-artifact-owner", owner.getId(), "credit_card_installment_campaign_v1", "COMPLETED")
        );

        assertThatThrownBy(() -> artifactRepository.saveAndFlush(
            new Artifact("artifact-mismatched-project", other.getId(), run.getId(), "# PRD")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
