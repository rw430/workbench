package com.xiaoc.workbench.growth.service;

import com.xiaoc.workbench.growth.domain.Lesson;
import com.xiaoc.workbench.growth.domain.Reflection;
import com.xiaoc.workbench.growth.repository.LessonRepository;
import com.xiaoc.workbench.growth.repository.ReflectionRepository;
import com.xiaoc.workbench.orchestrator.domain.Artifact;
import com.xiaoc.workbench.orchestrator.domain.OrchestratorRun;
import com.xiaoc.workbench.orchestrator.domain.OrchestratorTask;
import com.xiaoc.workbench.orchestrator.repository.ArtifactRepository;
import com.xiaoc.workbench.orchestrator.repository.OrchestratorTaskRepository;
import com.xiaoc.workbench.project.domain.Project;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeliveryGenerationService {
    private final ArtifactRepository artifactRepository;
    private final ReflectionRepository reflectionRepository;
    private final LessonRepository lessonRepository;
    private final OrchestratorTaskRepository taskRepository;

    public DeliveryGenerationService(
            ArtifactRepository artifactRepository,
            ReflectionRepository reflectionRepository,
            LessonRepository lessonRepository,
            OrchestratorTaskRepository taskRepository
    ) {
        this.artifactRepository = artifactRepository;
        this.reflectionRepository = reflectionRepository;
        this.lessonRepository = lessonRepository;
        this.taskRepository = taskRepository;
    }

    public void generateIfMissing(Project project, OrchestratorRun run) {
        Artifact artifact = artifactRepository.findByRunId(run.getId())
                .orElseGet(() -> artifactRepository.save(new Artifact(
                        id("artifact"),
                        project.getId(),
                        run.getId(),
                        artifactContent(project, run))));

        Reflection reflection = reflectionRepository.findByRunId(run.getId())
                .orElseGet(() -> reflectionRepository.save(new Reflection(
                        id("reflection"),
                        project.getId(),
                        run.getId(),
                        reflectionContent(project, run, artifact))));

        if (lessonRepository.findAllByReflectionId(reflection.getId()).isEmpty()) {
            lessonRepository.saveAll(List.of(
                    new Lesson(id("lesson"), reflection.getId(), "scope_control",
                            "HumanGate 将 PRD 范围确认放在风险评审前，能减少后续返工和错误扩散。", "high"),
                    new Lesson(id("lesson"), reflection.getId(), "risk_compliance",
                            "银行信用卡分期活动必须显式记录风险、合规、灰度和操作审计边界。", "high"),
                    new Lesson(id("lesson"), reflection.getId(), "delivery_quality",
                            "DAG 输出按需求、风控、技术、测试、交付和复盘分层沉淀，便于面试讲清楚端到端闭环。", "medium")
            ));
        }
    }

    private String artifactContent(Project project, OrchestratorRun run) {
        List<OrchestratorTask> tasks = taskRepository.findAllByRunIdOrderBySortOrderAsc(run.getId());
        String taskSummary = tasks.stream()
                .filter(task -> task.getOutput() != null && !task.getOutput().isBlank())
                .sorted(Comparator.comparingInt(OrchestratorTask::getSortOrder))
                .map(task -> "- " + task.getName() + ": " + task.getOutput())
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- 所有任务已按 DAG 完成。");
        return "信用卡分期活动研发交付物" + System.lineSeparator()
                + "项目目标: " + project.getGoal() + System.lineSeparator()
                + "模板: " + run.getTemplateId() + System.lineSeparator()
                + "任务输出:" + System.lineSeparator()
                + taskSummary;
    }

    private String reflectionContent(Project project, OrchestratorRun run, Artifact artifact) {
        return "复盘" + System.lineSeparator()
                + "项目 " + project.getId() + " 已完成 " + run.getTemplateId() + " 黄金路径。" + System.lineSeparator()
                + "交付物 " + artifact.getId() + " 汇总了需求、风险、技术、测试和交付任务输出。" + System.lineSeparator()
                + "后续可以接入真实 LLM、RabbitMQ worker 和更细粒度权限。";
    }

    private String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
