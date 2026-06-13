package com.xiaoc.workbench.orchestrator.service;

import com.xiaoc.workbench.common.web.InvalidStateException;
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

    public RunnerService(
            ProjectRepository projectRepository,
            OrchestratorRunRepository runRepository,
            OrchestratorTaskRepository taskRepository,
            TaskEdgeRepository edgeRepository,
            HumanGateRepository humanGateRepository,
            ProjectApplicationService projectApplicationService,
            DeterministicTaskExecutor taskExecutor
    ) {
        this.projectRepository = projectRepository;
        this.runRepository = runRepository;
        this.taskRepository = taskRepository;
        this.edgeRepository = edgeRepository;
        this.humanGateRepository = humanGateRepository;
        this.projectApplicationService = projectApplicationService;
        this.taskExecutor = taskExecutor;
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

        gate.approve(normalizeReason(reason), normalizeDecidedBy(decidedBy));
        task.complete("approved by " + normalizeDecidedBy(decidedBy) + ": " + normalizeReason(reason),
                "human gate approved");
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
            gate.reject(normalizeReason(reason), normalizeDecidedBy(decidedBy));
            task.fail("human gate rejected: " + normalizeReason(reason));
            project.markFailed();
            run.markFailed();
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
                }
                return;
            }

            if ("HUMAN_GATE".equals(ready.getKind())) {
                ready.markWaitingHuman("waiting for human approval");
                project.markWaitingHuman();
                run.markWaitingHuman();
                humanGateRepository.findByTaskId(ready.getId())
                        .orElseGet(() -> humanGateRepository.save(new HumanGate(
                                id("gate"),
                                run.getId(),
                                ready.getId(),
                                "WAITING",
                                "Confirm " + ready.getName() + " before continuing.")));
                return;
            }

            ready.markRunning();
            ready.complete(taskExecutor.execute(ready), "executed by deterministic local runner");
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
