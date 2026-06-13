package com.xiaoc.workbench.growth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xiaoc.workbench.agent.domain.AgentProfile;
import com.xiaoc.workbench.agent.repository.AgentProfileRepository;
import com.xiaoc.workbench.governance.domain.AuditLog;
import com.xiaoc.workbench.governance.repository.AuditLogRepository;
import com.xiaoc.workbench.growth.domain.EvolutionRecord;
import com.xiaoc.workbench.growth.domain.Lesson;
import com.xiaoc.workbench.growth.domain.Reflection;
import com.xiaoc.workbench.orchestrator.domain.OrchestratorRun;
import com.xiaoc.workbench.orchestrator.repository.OrchestratorRunRepository;
import com.xiaoc.workbench.project.domain.Project;
import com.xiaoc.workbench.project.repository.ProjectRepository;
import com.xiaoc.workbench.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class GrowthAuditRepositoryTest extends PostgresIntegrationTest {
    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private OrchestratorRunRepository runRepository;

    @Autowired
    private AgentProfileRepository agentProfileRepository;

    @Autowired
    private ReflectionRepository reflectionRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EvolutionRecordRepository evolutionRecordRepository;

    @Test
    void persistsReflectionLessonEvolutionRecordAndAuditLog() {
        Project project = projectRepository.save(
            new Project("project-001", "信用卡分期活动配置与审批系统研发方案", "TASKS", "WAITING_HUMAN")
        );
        OrchestratorRun run = runRepository.save(
            new OrchestratorRun("run-001", project.getId(), "credit_card_installment_campaign_v1", "WAITING_HUMAN")
        );
        AgentProfile agent = agentProfileRepository.save(
            new AgentProfile(
                "agent-pd-default",
                "产品经理",
                "PD",
                "负责需求分析和 PRD 输出",
                "需求分析,PRD,验收标准",
                "信用卡,分期活动,产品设计",
                new BigDecimal("0.9500"),
                "匹配产品需求分析任务",
                1
            )
        );

        Reflection reflection = reflectionRepository.save(
            new Reflection("reflection-001", project.getId(), run.getId(), "# 任务复盘\n\n本次任务完成 PRD 到交付闭环。")
        );
        lessonRepository.save(
            new Lesson("lesson-001", reflection.getId(), "prd-quality", "HumanGate 前必须明确验收口径。", "HIGH")
        );
        auditLogRepository.save(
            new AuditLog(
                "audit-001",
                "user-admin",
                "HUMAN_GATE_APPROVE",
                "human_gate",
                "gate-001",
                Map.of("run_id", run.getId())
            )
        );
        evolutionRecordRepository.save(new EvolutionRecord("evolution-001", agent.getId(), "提升 PRD 验收清晰度"));

        assertThat(reflectionRepository.findByRunId(run.getId())).isPresent();
        assertThat(lessonRepository.findAllByReflectionId(reflection.getId())).hasSize(1);
        assertThat(evolutionRecordRepository.findById("evolution-001").orElseThrow().getAgentId())
            .isEqualTo(agent.getId());
        assertThat(auditLogRepository.findAllByActorIdOrderByCreatedAtDescIdDesc("user-admin"))
            .extracting(AuditLog::getAction)
            .containsExactly("HUMAN_GATE_APPROVE");
        assertThat(auditLogRepository.findById("audit-001").orElseThrow().getPayload())
            .containsEntry("run_id", run.getId());
    }

    @Test
    void rejectsReflectionWhenProjectDoesNotOwnRun() {
        Project owner = projectRepository.save(
            new Project("project-reflection-owner", "真实项目", "TASKS", "COMPLETED")
        );
        Project other = projectRepository.save(
            new Project("project-reflection-other", "错误项目", "TASKS", "COMPLETED")
        );
        OrchestratorRun run = runRepository.save(
            new OrchestratorRun("run-reflection-owner", owner.getId(), "credit_card_installment_campaign_v1", "COMPLETED")
        );

        assertThatThrownBy(() -> reflectionRepository.saveAndFlush(
            new Reflection("reflection-mismatched-project", other.getId(), run.getId(), "# 复盘")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
