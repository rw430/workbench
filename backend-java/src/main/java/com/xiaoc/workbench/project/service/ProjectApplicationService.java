package com.xiaoc.workbench.project.service;

import com.xiaoc.workbench.agent.api.AgentSummary;
import com.xiaoc.workbench.agent.service.AgentRecommendationService;
import com.xiaoc.workbench.intent.service.IntentAnalysis;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import com.xiaoc.workbench.orchestrator.domain.HumanGate;
import com.xiaoc.workbench.orchestrator.domain.OrchestratorRun;
import com.xiaoc.workbench.orchestrator.domain.OrchestratorTask;
import com.xiaoc.workbench.orchestrator.domain.TaskEdge;
import com.xiaoc.workbench.orchestrator.repository.HumanGateRepository;
import com.xiaoc.workbench.orchestrator.repository.OrchestratorRunRepository;
import com.xiaoc.workbench.orchestrator.repository.OrchestratorTaskRepository;
import com.xiaoc.workbench.orchestrator.repository.TaskEdgeRepository;
import com.xiaoc.workbench.orchestrator.template.DagTemplate;
import com.xiaoc.workbench.orchestrator.template.DagTemplateLoader;
import com.xiaoc.workbench.project.api.HumanGateSummary;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.api.ProjectSummary;
import com.xiaoc.workbench.project.api.RoomSummary;
import com.xiaoc.workbench.project.api.RunSummary;
import com.xiaoc.workbench.project.api.TaskSummary;
import com.xiaoc.workbench.project.domain.Project;
import com.xiaoc.workbench.project.domain.Room;
import com.xiaoc.workbench.project.repository.ProjectRepository;
import com.xiaoc.workbench.project.repository.RoomRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectApplicationService {
    private static final String ROOM_NAME = "信用卡分期活动研发协同室";

    private final IntentAnalysisService intentAnalysisService;
    private final AgentRecommendationService agentRecommendationService;
    private final DagTemplateLoader dagTemplateLoader;
    private final ProjectRepository projectRepository;
    private final RoomRepository roomRepository;
    private final OrchestratorRunRepository runRepository;
    private final OrchestratorTaskRepository taskRepository;
    private final TaskEdgeRepository edgeRepository;
    private final HumanGateRepository humanGateRepository;

    public ProjectApplicationService(
            IntentAnalysisService intentAnalysisService,
            AgentRecommendationService agentRecommendationService,
            DagTemplateLoader dagTemplateLoader,
            ProjectRepository projectRepository,
            RoomRepository roomRepository,
            OrchestratorRunRepository runRepository,
            OrchestratorTaskRepository taskRepository,
            TaskEdgeRepository edgeRepository,
            HumanGateRepository humanGateRepository
    ) {
        this.intentAnalysisService = intentAnalysisService;
        this.agentRecommendationService = agentRecommendationService;
        this.dagTemplateLoader = dagTemplateLoader;
        this.projectRepository = projectRepository;
        this.roomRepository = roomRepository;
        this.runRepository = runRepository;
        this.taskRepository = taskRepository;
        this.edgeRepository = edgeRepository;
        this.humanGateRepository = humanGateRepository;
    }

    @Transactional
    public ProjectStateResponse createProject(String goal) {
        String trimmedGoal = goal == null ? "" : goal.strip();
        if (trimmedGoal.isBlank()) {
            throw new IllegalArgumentException("goal must not be blank");
        }

        IntentAnalysis intent = intentAnalysisService.analyze(trimmedGoal);
        DagTemplate template = dagTemplateLoader.load(intent.templateId());
        Project project = projectRepository.save(new Project(id("project"), trimmedGoal, intent.mode(), "CREATED"));
        Room room = roomRepository.save(new Room(id("room"), project.getId(), ROOM_NAME));
        OrchestratorRun run = runRepository.save(
                new OrchestratorRun(id("run"), project.getId(), template.id(), "CREATED"));

        saveTasks(run, template);
        saveEdges(run, template);

        return assembleState(project, room, run, agentRecommendationService.recommend(intent));
    }

    @Transactional(readOnly = true)
    public ProjectStateResponse getProject(String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Project not found: " + projectId));
        Room room = roomRepository.findByProjectId(projectId)
                .orElseThrow(() -> new NoSuchElementException("Room not found for project: " + projectId));
        OrchestratorRun run = runRepository.findByProjectId(projectId)
                .orElseThrow(() -> new NoSuchElementException("Run not found for project: " + projectId));
        IntentAnalysis intent = intentAnalysisService.analyze(project.getGoal());
        return assembleState(project, room, run, agentRecommendationService.recommend(intent));
    }

    @Transactional(readOnly = true)
    public ProjectStateResponse getRunState(String runId) {
        OrchestratorRun run = runRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Run not found: " + runId));
        Project project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new NoSuchElementException("Project not found: " + run.getProjectId()));
        Room room = roomRepository.findByProjectId(project.getId())
                .orElseThrow(() -> new NoSuchElementException("Room not found for project: " + project.getId()));
        IntentAnalysis intent = intentAnalysisService.analyze(project.getGoal());
        return assembleState(project, room, run, agentRecommendationService.recommend(intent));
    }

    private void saveTasks(OrchestratorRun run, DagTemplate template) {
        List<OrchestratorTask> tasks = new ArrayList<>();
        for (int index = 0; index < template.nodes().size(); index++) {
            DagTemplate.Node node = template.nodes().get(index);
            String status = node.dependsOn().isEmpty() ? "READY" : "PENDING";
            tasks.add(new OrchestratorTask(
                    id("task"),
                    run.getId(),
                    node.id(),
                    node.name(),
                    node.kind(),
                    node.role(),
                    status,
                    "",
                    "",
                    index));
        }
        taskRepository.saveAllAndFlush(tasks);
    }

    private void saveEdges(OrchestratorRun run, DagTemplate template) {
        List<TaskEdge> edges = template.nodes().stream()
                .flatMap(node -> node.dependsOn().stream()
                        .map(dependency -> new TaskEdge(id("edge"), run.getId(), dependency, node.id())))
                .toList();
        edgeRepository.saveAll(edges);
    }

    private ProjectStateResponse assembleState(
            Project project,
            Room room,
            OrchestratorRun run,
            List<AgentSummary> agents
    ) {
        List<OrchestratorTask> tasks = taskRepository.findAllByRunIdOrderBySortOrderAsc(run.getId());
        Map<String, List<String>> dependenciesByTarget = edgeRepository.findAllByRunId(run.getId()).stream()
                .sorted(Comparator.comparing(TaskEdge::getTargetNodeId).thenComparing(TaskEdge::getSourceNodeId))
                .collect(Collectors.groupingBy(
                        TaskEdge::getTargetNodeId,
                        Collectors.mapping(TaskEdge::getSourceNodeId, Collectors.toList())));
        HumanGateSummary humanGate = humanGateRepository.findFirstByRunIdOrderByCreatedAtDesc(run.getId())
                .map(this::toHumanGateSummary)
                .orElse(null);

        return new ProjectStateResponse(
                new ProjectSummary(project.getId(), project.getGoal(), lower(project.getMode()), lower(project.getStatus())),
                new RoomSummary(room.getId(), room.getProjectId(), room.getName()),
                agents,
                new RunSummary(run.getId(), run.getProjectId(), run.getTemplateId(), lower(run.getStatus())),
                tasks.stream()
                        .map(task -> toTaskSummary(task, dependenciesByTarget.getOrDefault(task.getNodeId(), List.of())))
                        .toList(),
                humanGate,
                null,
                null);
    }

    private TaskSummary toTaskSummary(OrchestratorTask task, List<String> dependsOn) {
        return new TaskSummary(
                task.getId(),
                task.getRunId(),
                task.getNodeId(),
                task.getName(),
                lower(task.getKind()),
                task.getRole(),
                dependsOn,
                lower(task.getStatus()),
                task.getOutput(),
                task.getLog());
    }

    private HumanGateSummary toHumanGateSummary(HumanGate gate) {
        return new HumanGateSummary(
                gate.getId(),
                gate.getRunId(),
                gate.getTaskId(),
                lower(gate.getStatus()),
                gate.getPrompt());
    }

    private String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
