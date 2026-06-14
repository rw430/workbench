package com.xiaoc.workbench.orchestrator.service;

import com.xiaoc.workbench.common.web.InvalidStateException;
import com.xiaoc.workbench.event.service.RuntimeEventService;
import com.xiaoc.workbench.governance.service.AuditLogService;
import com.xiaoc.workbench.growth.service.DeliveryGenerationService;
import com.xiaoc.workbench.orchestrator.domain.HumanGate;
import com.xiaoc.workbench.orchestrator.domain.OrchestratorRun;
import com.xiaoc.workbench.orchestrator.domain.OrchestratorTask;
import com.xiaoc.workbench.orchestrator.domain.TaskEdge;
import com.xiaoc.workbench.orchestrator.repository.HumanGateRepository;
import com.xiaoc.workbench.orchestrator.repository.OrchestratorRunRepository;
import com.xiaoc.workbench.orchestrator.repository.OrchestratorTaskRepository;
import com.xiaoc.workbench.orchestrator.repository.TaskEdgeRepository;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.domain.Project;
import com.xiaoc.workbench.project.repository.ProjectRepository;
import com.xiaoc.workbench.project.service.ProjectApplicationService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunnerService {
    private final ProjectRepository projectRepository;
    private final OrchestratorRunRepository runRepository;
    private final OrchestratorTaskRepository taskRepository;
    private final TaskEdgeRepository edgeRepository;
    private final HumanGateRepository humanGateRepository;
    private final ProjectApplicationService projectApplicationService;
    private final DeterministicTaskExecutor taskExecutor;
    private final RuntimeEventService runtimeEventService;
    private final AuditLogService auditLogService;
    private final DeliveryGenerationService deliveryGenerationService;

    public RunnerService(
            ProjectRepository projectRepository,
            OrchestratorRunRepository runRepository,
            OrchestratorTaskRepository taskRepository,
            TaskEdgeRepository edgeRepository,
            HumanGateRepository humanGateRepository,
            ProjectApplicationService projectApplicationService,
            DeterministicTaskExecutor taskExecutor,
            RuntimeEventService runtimeEventService,
            AuditLogService auditLogService,
            DeliveryGenerationService deliveryGenerationService
    ) {
        this.projectRepository = projectRepository;
        this.runRepository = runRepository;
        this.taskRepository = taskRepository;
        this.edgeRepository = edgeRepository;
        this.humanGateRepository = humanGateRepository;
        this.projectApplicationService = projectApplicationService;
        this.taskExecutor = taskExecutor;
        this.runtimeEventService = runtimeEventService;
        this.auditLogService = auditLogService;
        this.deliveryGenerationService = deliveryGenerationService;
    }

    @Transactional
    public ProjectStateResponse startRun(String runId) {
        OrchestratorRun run = loadRun(runId);
        if (isStopped(run)) {
            return projectApplicationService.getRunState(run.getId());
        }

        Project project = loadProject(run.getProjectId());
        project.markRunning();
        run.markRunning();
        runtimeEventService.append(run.getId(), "run.started", Map.of(
                "project_id", project.getId(),
                "run_id", run.getId(),
                "status", "running"));
        auditLogService.record("local-user", "RUN_START", "run", run.getId(), Map.of(
                "project_id", project.getId(),
                "template_id", run.getTemplateId()));
        advance(project, run);
        return projectApplicationService.getRunState(run.getId());
    }

    @Transactional
    public ProjectStateResponse approveGate(String gateId, String reason, String decidedBy) {
        HumanGate gate = loadGate(gateId);
        if ("REJECTED".equals(gate.getStatus())) {
            throw new InvalidStateException("Human gate was already rejected: " + gateId);
        }
        if ("APPROVED".equals(gate.getStatus())) {
            return projectApplicationService.getRunState(gate.getRunId());
        }

        OrchestratorRun run = loadRun(gate.getRunId());
        Project project = loadProject(run.getProjectId());
        OrchestratorTask task = taskRepository.findById(gate.getTaskId())
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + gate.getTaskId()));

        String normalizedReason = normalizeReason(reason);
        String actor = normalizeDecidedBy(decidedBy);
        gate.approve(normalizedReason, actor);
        task.complete("approved by " + actor + ": " + normalizedReason,
                "human gate approved");
        runtimeEventService.append(run.getId(), "human_gate.approved", Map.of(
                "gate_id", gate.getId(),
                "task_id", task.getId(),
                "reason", normalizedReason,
                "decided_by", actor));
        runtimeEventService.append(run.getId(), "task.completed", Map.of(
                "task_id", task.getId(),
                "node_id", task.getNodeId(),
                "kind", task.getKind(),
                "status", "completed"));
        auditLogService.record(actor, "HUMAN_GATE_APPROVE", "human_gate", gate.getId(), Map.of(
                "run_id", run.getId(),
                "task_id", task.getId(),
                "reason", normalizedReason));
        project.markRunning();
        run.markRunning();
        advance(project, run);
        return projectApplicationService.getRunState(run.getId());
    }

    @Transactional
    public ProjectStateResponse rejectGate(String gateId, String reason, String decidedBy) {
        HumanGate gate = loadGate(gateId);
        if ("APPROVED".equals(gate.getStatus())) {
            throw new InvalidStateException("Human gate was already approved: " + gateId);
        }
        OrchestratorRun run = loadRun(gate.getRunId());
        Project project = loadProject(run.getProjectId());
        OrchestratorTask task = taskRepository.findById(gate.getTaskId())
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + gate.getTaskId()));

        if (!"REJECTED".equals(gate.getStatus())) {
            String normalizedReason = normalizeReason(reason);
            String actor = normalizeDecidedBy(decidedBy);
            gate.reject(normalizedReason, actor);
            task.fail("human gate rejected: " + normalizedReason);
            project.markFailed();
            run.markFailed();
            runtimeEventService.append(run.getId(), "human_gate.rejected", Map.of(
                    "gate_id", gate.getId(),
                    "task_id", task.getId(),
                    "reason", normalizedReason,
                    "decided_by", actor));
            runtimeEventService.append(run.getId(), "run.failed", Map.of(
                    "project_id", project.getId(),
                    "run_id", run.getId(),
                    "reason", normalizedReason));
            auditLogService.record(actor, "HUMAN_GATE_REJECT", "human_gate", gate.getId(), Map.of(
                    "run_id", run.getId(),
                    "task_id", task.getId(),
                    "reason", normalizedReason));
            auditLogService.record(actor, "RUN_FAILED", "run", run.getId(), Map.of(
                    "project_id", project.getId(),
                    "reason", normalizedReason));
        }

        return projectApplicationService.getRunState(run.getId());
    }

    private void advance(Project project, OrchestratorRun run) {
        while (true) {
            List<OrchestratorTask> tasks = taskRepository.findAllByRunIdOrderBySortOrderAsc(run.getId());
            List<TaskEdge> edges = edgeRepository.findAllByRunId(run.getId());
            Map<String, OrchestratorTask> tasksByNodeId = tasks.stream()
                    .collect(Collectors.toMap(OrchestratorTask::getNodeId, Function.identity()));

            unlockReadyTasks(tasks, edges, tasksByNodeId);

            OrchestratorTask ready = tasks.stream()
                    .filter(task -> "READY".equals(task.getStatus()))
                    .min(Comparator.comparingInt(OrchestratorTask::getSortOrder))
                    .orElse(null);

            if (ready == null) {
                if (tasks.stream().allMatch(task -> "COMPLETED".equals(task.getStatus()))) {
                    project.markCompleted();
                    run.markCompleted();
                    deliveryGenerationService.generateIfMissing(project, run);
                    runtimeEventService.append(run.getId(), "run.completed", Map.of(
                            "project_id", project.getId(),
                            "run_id", run.getId(),
                            "status", "completed"));
                    auditLogService.record("local-user", "RUN_COMPLETED", "run", run.getId(), Map.of(
                            "project_id", project.getId()));
                }
                return;
            }

            if ("HUMAN_GATE".equals(ready.getKind())) {
                ready.markWaitingHuman("waiting for human approval");
                project.markWaitingHuman();
                run.markWaitingHuman();
                HumanGate gate = humanGateRepository.findByTaskId(ready.getId())
                        .orElseGet(() -> humanGateRepository.save(new HumanGate(
                                id("gate"),
                                run.getId(),
                                ready.getId(),
                                "WAITING",
                                "Confirm " + ready.getName() + " before continuing.")));
                runtimeEventService.append(run.getId(), "human_gate.waiting", Map.of(
                        "gate_id", gate.getId(),
                        "task_id", ready.getId(),
                        "node_id", ready.getNodeId(),
                        "status", "waiting"));
                return;
            }

            ready.markRunning();
            ready.complete(taskExecutor.execute(ready), "executed by deterministic local runner");
            runtimeEventService.append(run.getId(), "task.completed", Map.of(
                    "task_id", ready.getId(),
                    "node_id", ready.getNodeId(),
                    "kind", ready.getKind(),
                    "status", "completed"));
        }
    }

    private void unlockReadyTasks(
            List<OrchestratorTask> tasks,
            List<TaskEdge> edges,
            Map<String, OrchestratorTask> tasksByNodeId
    ) {
        tasks.stream()
                .filter(task -> "PENDING".equals(task.getStatus()))
                .filter(task -> dependenciesComplete(task, edges, tasksByNodeId))
                .forEach(OrchestratorTask::markReady);
    }

    private boolean dependenciesComplete(
            OrchestratorTask task,
            List<TaskEdge> edges,
            Map<String, OrchestratorTask> tasksByNodeId
    ) {
        return edges.stream()
                .filter(edge -> edge.getTargetNodeId().equals(task.getNodeId()))
                .map(TaskEdge::getSourceNodeId)
                .allMatch(source -> "COMPLETED".equals(tasksByNodeId.get(source).getStatus()));
    }

    private boolean isStopped(OrchestratorRun run) {
        return "WAITING_HUMAN".equals(run.getStatus())
                || "COMPLETED".equals(run.getStatus())
                || "FAILED".equals(run.getStatus());
    }

    private OrchestratorRun loadRun(String runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Run not found: " + runId));
    }

    private Project loadProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Project not found: " + projectId));
    }

    private HumanGate loadGate(String gateId) {
        return humanGateRepository.findById(gateId)
                .orElseThrow(() -> new NoSuchElementException("Human gate not found: " + gateId));
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "no reason provided" : reason.strip();
    }

    private String normalizeDecidedBy(String decidedBy) {
        return decidedBy == null || decidedBy.isBlank() ? "local-user" : decidedBy.strip();
    }

    private String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
