# Phase 5 Frontend Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase 5 React/TypeScript operational workbench and add the minimal backend delivery data loop needed for Artifact / Reflection / Lessons to display real persisted content.

**Architecture:** The backend adds a narrow deterministic `DeliveryGenerationService` used by `RunnerService` at run completion and exposed through `ProjectStateResponse`. The frontend remains a Vite React app, with `App.tsx` orchestrating workflow state and focused components rendering Goal, Intent, Agents, Run/DAG, HumanGate, Events, Audit, and Delivery panels.

**Tech Stack:** Java 21, Spring Boot 3.3.6, Spring Data JPA, PostgreSQL/Testcontainers, JUnit 5, React 18, TypeScript, Vite, Vitest, Testing Library, lucide-react.

---

## File Structure

Backend:

- Create: `backend-java/src/main/java/com/xiaoc/workbench/project/api/LessonSummary.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/growth/service/DeliveryGenerationService.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectStateResponse.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/RunnerService.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/service/RunnerDeliveryIntegrationTest.java`
- Update affected tests importing `ProjectApplicationService` or asserting `ProjectStateResponse`.

Frontend:

- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api.ts`
- Replace: `frontend/src/App.tsx`
- Modify: `frontend/src/App.css`
- Create: `frontend/src/components/GoalPanel.tsx`
- Create: `frontend/src/components/IntentPanel.tsx`
- Create: `frontend/src/components/AgentPanel.tsx`
- Create: `frontend/src/components/RunControl.tsx`
- Create: `frontend/src/components/DagBoard.tsx`
- Create: `frontend/src/components/HumanGatePanel.tsx`
- Create: `frontend/src/components/EventTimeline.tsx`
- Create: `frontend/src/components/AuditPanel.tsx`
- Create: `frontend/src/components/DeliveryPanel.tsx`
- Replace: `frontend/src/App.test.tsx`

Docs:

- Create: `docs/learning/phase-05-frontend-workbench.md`

---

### Task 1: Backend Delivery Data Loop

**Files:**
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/service/RunnerDeliveryIntegrationTest.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/project/api/LessonSummary.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/growth/service/DeliveryGenerationService.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectStateResponse.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/RunnerService.java`

- [ ] **Step 1: Write the failing backend integration test**

Create `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/service/RunnerDeliveryIntegrationTest.java`:

```java
package com.xiaoc.workbench.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoc.workbench.agent.service.AgentRecommendationService;
import com.xiaoc.workbench.agent.service.BuiltinAgentSeeder;
import com.xiaoc.workbench.event.service.RuntimeEventService;
import com.xiaoc.workbench.governance.service.AuditLogService;
import com.xiaoc.workbench.growth.repository.LessonRepository;
import com.xiaoc.workbench.growth.repository.ReflectionRepository;
import com.xiaoc.workbench.growth.service.DeliveryGenerationService;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import com.xiaoc.workbench.orchestrator.repository.ArtifactRepository;
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
        DeliveryGenerationService.class,
        ProjectApplicationService.class,
        DeterministicTaskExecutor.class,
        RunnerService.class,
        RunnerDeliveryIntegrationTest.JacksonTestConfig.class
})
class RunnerDeliveryIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private BuiltinAgentSeeder seeder;

    @Autowired
    private ProjectApplicationService projectService;

    @Autowired
    private RunnerService runnerService;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private ReflectionRepository reflectionRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Test
    void approvalCompletionCreatesDeliveryDataAndReturnsItInProjectState() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign delivery");
        ProjectStateResponse waiting = runnerService.startRun(created.run().id());

        ProjectStateResponse completed = runnerService.approveGate(
                waiting.humanGate().id(),
                "scope confirmed",
                "local-user");

        assertThat(completed.run().status()).isEqualTo("completed");
        assertThat(completed.artifact()).contains("信用卡分期活动研发交付物");
        assertThat(completed.reflection()).contains("复盘");
        assertThat(completed.lessons()).extracting("category")
                .contains("scope_control", "risk_compliance", "delivery_quality");
        assertThat(artifactRepository.findByRunId(created.run().id())).isPresent();
        assertThat(reflectionRepository.findByRunId(created.run().id())).isPresent();
        assertThat(lessonRepository.findAllByReflectionId(
                reflectionRepository.findByRunId(created.run().id()).orElseThrow().getId()))
                .hasSize(3);
    }

    @Test
    void deliveryGenerationIsIdempotentForCompletedRuns() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign idempotent delivery");
        ProjectStateResponse waiting = runnerService.startRun(created.run().id());
        ProjectStateResponse completed = runnerService.approveGate(
                waiting.humanGate().id(),
                "scope confirmed",
                "local-user");

        runnerService.startRun(completed.run().id());
        ProjectStateResponse loaded = projectService.getRunState(completed.run().id());

        assertThat(artifactRepository.findAll()).hasSize(1);
        assertThat(reflectionRepository.findAll()).hasSize(1);
        assertThat(lessonRepository.findAll()).hasSize(3);
        assertThat(loaded.lessons()).hasSize(3);
    }

    @TestConfiguration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```powershell
cd backend-java
mvn "-Dtest=RunnerDeliveryIntegrationTest" test
```

Expected: compilation fails because `DeliveryGenerationService`, `LessonSummary`, and `ProjectStateResponse.lessons()` do not exist.

- [ ] **Step 3: Add the lesson DTO and ProjectState response field**

Create `backend-java/src/main/java/com/xiaoc/workbench/project/api/LessonSummary.java`:

```java
package com.xiaoc.workbench.project.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record LessonSummary(
        String id,
        @JsonProperty("reflection_id") String reflectionId,
        String category,
        String content,
        String confidence,
        @JsonProperty("created_at") Instant createdAt
) {
}
```

Modify `backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectStateResponse.java`:

```java
package com.xiaoc.workbench.project.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaoc.workbench.agent.api.AgentSummary;
import java.util.List;

public record ProjectStateResponse(
        ProjectSummary project,
        RoomSummary room,
        List<AgentSummary> agents,
        RunSummary run,
        List<TaskSummary> tasks,
        @JsonProperty("human_gate") HumanGateSummary humanGate,
        String artifact,
        String reflection,
        List<LessonSummary> lessons
) {
}
```

- [ ] **Step 4: Implement the deterministic delivery generation service**

Create `backend-java/src/main/java/com/xiaoc/workbench/growth/service/DeliveryGenerationService.java`:

```java
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
```

- [ ] **Step 5: Wire delivery generation into RunnerService**

Modify `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/RunnerService.java`:

```java
import com.xiaoc.workbench.growth.service.DeliveryGenerationService;
```

Add field:

```java
private final DeliveryGenerationService deliveryGenerationService;
```

Add constructor parameter after `AuditLogService auditLogService`:

```java
DeliveryGenerationService deliveryGenerationService
```

Assign it:

```java
this.deliveryGenerationService = deliveryGenerationService;
```

Inside `advance`, in the all-tasks-completed branch, call delivery generation before `run.completed` event:

```java
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
```

- [ ] **Step 6: Return persisted delivery data from ProjectApplicationService**

Modify imports in `backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java`:

```java
import com.xiaoc.workbench.growth.domain.Lesson;
import com.xiaoc.workbench.growth.repository.LessonRepository;
import com.xiaoc.workbench.growth.repository.ReflectionRepository;
import com.xiaoc.workbench.orchestrator.repository.ArtifactRepository;
import com.xiaoc.workbench.project.api.LessonSummary;
```

Add fields:

```java
private final ArtifactRepository artifactRepository;
private final ReflectionRepository reflectionRepository;
private final LessonRepository lessonRepository;
```

Add constructor parameters after `AuditLogService auditLogService`:

```java
ArtifactRepository artifactRepository,
ReflectionRepository reflectionRepository,
LessonRepository lessonRepository
```

Assign them:

```java
this.artifactRepository = artifactRepository;
this.reflectionRepository = reflectionRepository;
this.lessonRepository = lessonRepository;
```

In `assembleState`, before returning:

```java
String artifact = artifactRepository.findByRunId(run.getId())
        .map(com.xiaoc.workbench.orchestrator.domain.Artifact::getContent)
        .orElse(null);
String reflection = reflectionRepository.findByRunId(run.getId())
        .map(com.xiaoc.workbench.growth.domain.Reflection::getContent)
        .orElse(null);
List<LessonSummary> lessons = reflectionRepository.findByRunId(run.getId())
        .map(found -> lessonRepository.findAllByReflectionId(found.getId()).stream()
                .sorted(Comparator.comparing(Lesson::getCreatedAt).thenComparing(Lesson::getId))
                .map(this::toLessonSummary)
                .toList())
        .orElse(List.of());
```

Update the `ProjectStateResponse` constructor arguments:

```java
humanGate,
artifact,
reflection,
lessons);
```

Add helper:

```java
private LessonSummary toLessonSummary(Lesson lesson) {
    return new LessonSummary(
            lesson.getId(),
            lesson.getReflectionId(),
            lesson.getCategory(),
            lesson.getContent(),
            lesson.getConfidence(),
            lesson.getCreatedAt());
}
```

- [ ] **Step 7: Update affected backend tests**

In tests importing `ProjectApplicationService`, add these classes to `@Import` where needed:

```java
DeliveryGenerationService.class
```

Update assertions in `ProjectApplicationServiceTest.createsProjectStateFromGoldenPathTemplate`:

```java
assertThat(state.artifact()).isNull();
assertThat(state.reflection()).isNull();
assertThat(state.lessons()).isEmpty();
```

Update any direct `new ProjectStateResponse(...)` construction if compilation reports missing `lessons`.

- [ ] **Step 8: Run backend delivery tests GREEN**

Run:

```powershell
cd backend-java
mvn "-Dtest=RunnerDeliveryIntegrationTest,RunnerEventAuditIntegrationTest,RunnerServiceTest,ProjectApplicationServiceTest" test
```

Expected: all selected tests pass.

- [ ] **Step 9: Commit backend delivery loop**

Run:

```powershell
git add backend-java
git commit -m "feat: generate phase 5 delivery data"
```

---

### Task 2: Frontend Types And API Contract

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api.ts`
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: Replace frontend types with backend-aligned TypeScript contracts**

Modify `frontend/src/types.ts`:

```ts
export type IntentAnalysis = {
  mode: string;
  template_id: string;
  domain: string;
  risk_level: string;
  human_gate_required: boolean;
  confidence: number;
  candidate_roles: string[];
};

export type AgentSummary = {
  id: string;
  name: string;
  role: string;
  skills: string[];
  score: number;
  recommendation_reason: string;
};

export type ProjectSummary = {
  id: string;
  goal: string;
  mode: string;
  status: string;
};

export type RoomSummary = {
  id: string;
  project_id: string;
  name: string;
};

export type RunSummary = {
  id: string;
  project_id: string;
  template_id: string;
  status: string;
};

export type TaskSummary = {
  id: string;
  run_id: string;
  node_id: string;
  name: string;
  kind: string;
  role: string;
  depends_on: string[];
  status: string;
  output: string;
  log: string;
};

export type HumanGateSummary = {
  id: string;
  run_id: string;
  task_id: string;
  status: string;
  prompt: string;
};

export type LessonSummary = {
  id: string;
  reflection_id: string;
  category: string;
  content: string;
  confidence: string;
  created_at: string;
};

export type ProjectState = {
  project: ProjectSummary;
  room: RoomSummary;
  agents: AgentSummary[];
  run: RunSummary;
  tasks: TaskSummary[];
  human_gate: HumanGateSummary | null;
  artifact: string | null;
  reflection: string | null;
  lessons: LessonSummary[];
};

export type RuntimeEventEnvelope = {
  id: string;
  run_id: string;
  event_type: string;
  payload: Record<string, unknown>;
  created_at: string;
};

export type EventTimelineItem = {
  id: string;
  event_type: string;
  payload: Record<string, unknown>;
  created_at: string;
};

export type AuditLogSummary = {
  id: string;
  actor_id: string;
  action: string;
  target_type: string;
  target_id: string;
  payload: Record<string, unknown>;
  created_at: string;
};

export type HumanGateDecision = "approve" | "reject";
```

- [ ] **Step 2: Update API client functions**

Modify `frontend/src/api.ts`:

```ts
import type {
  AgentSummary,
  AuditLogSummary,
  HumanGateDecision,
  IntentAnalysis,
  ProjectState,
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE ?? "http://127.0.0.1:8889";

async function parseJsonResponse<T>(response: Response, action: string): Promise<T> {
  if (!response.ok) {
    throw new Error(`${action} failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function analyzeIntent(goal: string): Promise<IntentAnalysis> {
  const response = await fetch(`${API_BASE}/api/intent/analyze`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ goal }),
  });
  return parseJsonResponse<IntentAnalysis>(response, "Analyze intent");
}

export async function recommendAgents(goal: string): Promise<AgentSummary[]> {
  const response = await fetch(`${API_BASE}/api/agents/recommend`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ goal }),
  });
  return parseJsonResponse<AgentSummary[]>(response, "Recommend agents");
}

export async function createProject(goal: string): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/projects`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ goal }),
  });
  return parseJsonResponse<ProjectState>(response, "Create project");
}

export async function startRun(runId: string): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/runs/${runId}/start`, {
    method: "POST",
  });
  return parseJsonResponse<ProjectState>(response, "Start run");
}

export async function decideHumanGate(
  gateId: string,
  decision: HumanGateDecision,
  reason: string,
  decidedBy = "local-user",
): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/human-gates/${gateId}/${decision}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason, decided_by: decidedBy }),
  });
  return parseJsonResponse<ProjectState>(response, `Human gate ${decision}`);
}

export async function listAuditLogs(actorId = "local-user"): Promise<AuditLogSummary[]> {
  const params = new URLSearchParams({ actor_id: actorId });
  const response = await fetch(`${API_BASE}/api/audit-logs?${params.toString()}`);
  return parseJsonResponse<AuditLogSummary[]>(response, "List audit logs");
}

export function eventStreamUrl(runId: string): string {
  const params = new URLSearchParams({ run_id: runId });
  return `${API_BASE}/api/events/stream?${params.toString()}`;
}
```

- [ ] **Step 3: Run frontend type/build check**

Run:

```powershell
cd frontend
npm run build
```

Expected: build fails because `App.tsx` and tests still use the old `confirmHumanGate` API and number IDs.

- [ ] **Step 4: Do not commit yet**

This task intentionally leaves the app red until Task 3 updates `App.tsx` and tests to the new API contract.

---

### Task 3: Frontend Workbench Behavior And Components

**Files:**
- Replace: `frontend/src/App.test.tsx`
- Replace: `frontend/src/App.tsx`
- Create: `frontend/src/components/GoalPanel.tsx`
- Create: `frontend/src/components/IntentPanel.tsx`
- Create: `frontend/src/components/AgentPanel.tsx`
- Create: `frontend/src/components/RunControl.tsx`
- Create: `frontend/src/components/DagBoard.tsx`
- Create: `frontend/src/components/HumanGatePanel.tsx`
- Create: `frontend/src/components/EventTimeline.tsx`
- Create: `frontend/src/components/AuditPanel.tsx`
- Create: `frontend/src/components/DeliveryPanel.tsx`

- [ ] **Step 1: Replace App tests with golden path behavior tests**

Replace `frontend/src/App.test.tsx` with tests that mock API functions and `EventSource`:

```tsx
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import App from "./App";
import * as api from "./api";
import type { AgentSummary, AuditLogSummary, IntentAnalysis, ProjectState } from "./types";

const intent: IntentAnalysis = {
  mode: "tasks",
  template_id: "credit_card_installment_campaign_v1",
  domain: "banking_credit_card",
  risk_level: "medium",
  human_gate_required: true,
  confidence: 0.92,
  candidate_roles: ["PD", "DEV", "QA", "RISK", "PMO"],
};

const agents: AgentSummary[] = [
  {
    id: "agent-pd",
    name: "需求分析分身",
    role: "PD",
    skills: ["PRD", "范围澄清"],
    score: 0.96,
    recommendation_reason: "负责需求分析和 PRD 范围确认。",
  },
  {
    id: "agent-risk",
    name: "风险合规分身",
    role: "RISK",
    skills: ["合规", "审计"],
    score: 0.91,
    recommendation_reason: "负责银行业务边界和审计要求。",
  },
];

const waitingProjectState: ProjectState = {
  project: {
    id: "project-1",
    goal: "信用卡分期活动配置与审批系统研发方案",
    mode: "tasks",
    status: "waiting_human",
  },
  room: { id: "room-1", project_id: "project-1", name: "信用卡分期活动研发协同室" },
  agents,
  run: {
    id: "run-1",
    project_id: "project-1",
    template_id: "credit_card_installment_campaign_v1",
    status: "waiting_human",
  },
  tasks: [
    {
      id: "task-1",
      run_id: "run-1",
      node_id: "need_analysis",
      name: "需求分析",
      kind: "llm_prd_draft",
      role: "PD",
      depends_on: [],
      status: "completed",
      output: "PRD 草稿",
      log: "done",
    },
    {
      id: "task-2",
      run_id: "run-1",
      node_id: "human_gate_prd",
      name: "PRD 确认",
      kind: "human_gate",
      role: "USER",
      depends_on: ["need_analysis"],
      status: "waiting_human",
      output: "",
      log: "waiting",
    },
  ],
  human_gate: {
    id: "gate-1",
    run_id: "run-1",
    task_id: "task-2",
    status: "waiting",
    prompt: "Confirm PRD scope before risk review.",
  },
  artifact: null,
  reflection: null,
  lessons: [],
};

const completedProjectState: ProjectState = {
  ...waitingProjectState,
  project: { ...waitingProjectState.project, status: "completed" },
  run: { ...waitingProjectState.run, status: "completed" },
  human_gate: { ...waitingProjectState.human_gate!, status: "approved" },
  tasks: waitingProjectState.tasks.map((task) => ({ ...task, status: "completed" })),
  artifact: "信用卡分期活动研发交付物\n任务输出",
  reflection: "复盘\n后续可以接入真实 LLM。",
  lessons: [
    {
      id: "lesson-1",
      reflection_id: "reflection-1",
      category: "scope_control",
      content: "HumanGate 减少返工。",
      confidence: "high",
      created_at: "2026-06-14T00:00:00Z",
    },
  ],
};

const audits: AuditLogSummary[] = [
  {
    id: "audit-1",
    actor_id: "local-user",
    action: "HUMAN_GATE_APPROVE",
    target_type: "human_gate",
    target_id: "gate-1",
    payload: { reason: "scope confirmed" },
    created_at: "2026-06-14T00:00:00Z",
  },
];

type EventCallback = (event: MessageEvent<string>) => void;

class FakeEventSource {
  static instances: FakeEventSource[] = [];

  readonly url: string;
  readonly close = vi.fn();
  private readonly listeners = new Map<string, EventCallback[]>();

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, callback: EventCallback) {
    const callbacks = this.listeners.get(type) ?? [];
    callbacks.push(callback);
    this.listeners.set(type, callbacks);
  }

  removeEventListener(type: string, callback: EventCallback) {
    const callbacks = this.listeners.get(type) ?? [];
    this.listeners.set(
      type,
      callbacks.filter((candidate) => candidate !== callback),
    );
  }

  emit(type: string, data: string, lastEventId = "event-1") {
    const event = new MessageEvent(type, { data, lastEventId });
    for (const callback of this.listeners.get(type) ?? []) {
      callback(event);
    }
  }
}

function installFakeEventSource() {
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("Phase 5 workbench", () => {
  it("runs the golden path through intent, agents, project, start, approve, audit, and delivery", async () => {
    installFakeEventSource();
    vi.spyOn(api, "analyzeIntent").mockResolvedValue(intent);
    vi.spyOn(api, "recommendAgents").mockResolvedValue(agents);
    vi.spyOn(api, "createProject").mockResolvedValue({
      ...waitingProjectState,
      project: { ...waitingProjectState.project, status: "created" },
      run: { ...waitingProjectState.run, status: "created" },
      human_gate: null,
    });
    vi.spyOn(api, "startRun").mockResolvedValue(waitingProjectState);
    vi.spyOn(api, "decideHumanGate").mockResolvedValue(completedProjectState);
    vi.spyOn(api, "listAuditLogs").mockResolvedValue(audits);

    render(<App />);
    await userEvent.click(screen.getByRole("button", { name: /Analyze/i }));

    expect(await screen.findByText("banking_credit_card")).toBeInTheDocument();
    expect(screen.getByText("需求分析分身")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Create Project/i }));
    expect(await screen.findByText("credit_card_installment_campaign_v1")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Start Run/i }));
    expect(await screen.findByText("Confirm PRD scope before risk review.")).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText("Decision reason"));
    await userEvent.type(screen.getByLabelText("Decision reason"), "scope confirmed");
    await userEvent.click(screen.getByRole("button", { name: /Approve/i }));

    expect(await screen.findByText("信用卡分期活动研发交付物")).toBeInTheDocument();
    expect(screen.getByText("HUMAN_GATE_APPROVE")).toBeInTheDocument();
    expect(screen.getByText("HumanGate 减少返工。")).toBeInTheDocument();
  });

  it("subscribes to Phase 4 SSE envelopes and renders the event timeline", async () => {
    installFakeEventSource();
    vi.spyOn(api, "analyzeIntent").mockResolvedValue(intent);
    vi.spyOn(api, "recommendAgents").mockResolvedValue(agents);
    vi.spyOn(api, "createProject").mockResolvedValue(waitingProjectState);
    vi.spyOn(api, "listAuditLogs").mockResolvedValue([]);

    render(<App />);
    await userEvent.click(screen.getByRole("button", { name: /Analyze/i }));
    await userEvent.click(await screen.findByRole("button", { name: /Create Project/i }));

    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.instances[0].url).toContain("/api/events/stream?run_id=run-1");

    FakeEventSource.instances[0].emit(
      "task.completed",
      JSON.stringify({
        id: "event-1",
        run_id: "run-1",
        event_type: "task.completed",
        payload: { node_id: "need_analysis" },
        created_at: "2026-06-14T00:00:00Z",
      }),
    );

    expect(await screen.findByText("task.completed")).toBeInTheDocument();
    expect(screen.getByText(/need_analysis/)).toBeInTheDocument();
  });

  it("sends reject decisions to the backend", async () => {
    installFakeEventSource();
    vi.spyOn(api, "analyzeIntent").mockResolvedValue(intent);
    vi.spyOn(api, "recommendAgents").mockResolvedValue(agents);
    vi.spyOn(api, "createProject").mockResolvedValue(waitingProjectState);
    const decide = vi.spyOn(api, "decideHumanGate").mockResolvedValue({
      ...waitingProjectState,
      project: { ...waitingProjectState.project, status: "failed" },
      run: { ...waitingProjectState.run, status: "failed" },
      human_gate: { ...waitingProjectState.human_gate!, status: "rejected" },
    });
    vi.spyOn(api, "listAuditLogs").mockResolvedValue([]);

    render(<App />);
    await userEvent.click(screen.getByRole("button", { name: /Analyze/i }));
    await userEvent.click(await screen.findByRole("button", { name: /Create Project/i }));
    await userEvent.clear(await screen.findByLabelText("Decision reason"));
    await userEvent.type(screen.getByLabelText("Decision reason"), "scope too broad");
    await userEvent.click(screen.getByRole("button", { name: /Reject/i }));

    await waitFor(() => {
      expect(decide).toHaveBeenCalledWith("gate-1", "reject", "scope too broad", "local-user");
    });
  });
});
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
cd frontend
npm test
```

Expected: tests fail because components, API functions, and labels are not implemented yet.

- [ ] **Step 3: Create presentational components**

Create the listed component files using typed props from `types.ts`. Keep components stateless except `HumanGatePanel`, which receives `reason` and `onReasonChange` from `App`.

Minimum component contracts:

```tsx
// GoalPanel.tsx
type GoalPanelProps = {
  goal: string;
  busy: boolean;
  onGoalChange: (value: string) => void;
  onAnalyze: () => void;
  onCreateProject: () => void;
  canCreateProject: boolean;
};
```

```tsx
// HumanGatePanel.tsx
type HumanGatePanelProps = {
  gate: HumanGateSummary | null;
  reason: string;
  busy: boolean;
  onReasonChange: (value: string) => void;
  onApprove: () => void;
  onReject: () => void;
};
```

Each component should render stable accessible labels matching the test names:

- `Analyze`
- `Create Project`
- `Start Run`
- `Approve`
- `Reject`
- `Decision reason`

- [ ] **Step 4: Replace App orchestration**

Replace `frontend/src/App.tsx` with orchestration that:

- imports new API functions;
- owns `goal`, `intent`, `recommendedAgents`, `projectState`, `events`, `auditLogs`, `decisionReason`, `busyAction`, and `error`;
- implements `handleAnalyze`, `handleCreateProject`, `handleStartRun`, `handleDecision`, and `refreshAudit`;
- opens EventSource when `projectState.run.id` exists;
- listens for exactly these event names:

```ts
const runtimeEventTypes = [
  "project.created",
  "run.started",
  "task.completed",
  "human_gate.waiting",
  "human_gate.approved",
  "human_gate.rejected",
  "run.completed",
  "run.failed",
];
```

The SSE handler should parse data:

```ts
const envelope = JSON.parse(event.data) as RuntimeEventEnvelope;
setEvents((current) => {
  if (current.some((item) => item.id === envelope.id)) {
    return current;
  }
  return [
    ...current,
    {
      id: envelope.id,
      event_type: envelope.event_type,
      payload: envelope.payload,
      created_at: envelope.created_at,
    },
  ].slice(-30);
});
```

- [ ] **Step 5: Run frontend tests GREEN**

Run:

```powershell
cd frontend
npm test
```

Expected: all frontend tests pass.

- [ ] **Step 6: Run frontend build GREEN**

Run:

```powershell
cd frontend
npm run build
```

Expected: TypeScript and Vite build succeed.

- [ ] **Step 7: Commit frontend contract and behavior**

Run:

```powershell
git add frontend
git commit -m "feat: build phase 5 frontend workbench"
```

---

### Task 4: Frontend Visual Polish And Responsive Layout

**Files:**
- Modify: `frontend/src/App.css`

- [ ] **Step 1: Rewrite CSS for the operational layout**

Use a desktop grid:

```css
.workspace-shell {
  display: grid;
  grid-template-columns: minmax(250px, 300px) minmax(0, 1fr) minmax(280px, 340px);
  gap: 16px;
}
```

Use mobile stacking:

```css
@media (max-width: 980px) {
  .workspace-shell {
    grid-template-columns: 1fr;
  }
}
```

Use compact panels:

```css
.panel {
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #ffffff;
  padding: 14px;
}
```

Use stable task tiles:

```css
.task-tile {
  min-height: 136px;
  border-radius: 6px;
  overflow-wrap: anywhere;
}
```

Avoid large hero typography, decorative gradients, nested cards, and text overflow.

- [ ] **Step 2: Run frontend tests and build after CSS changes**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: tests and build pass.

- [ ] **Step 3: Commit visual polish**

Run:

```powershell
git add frontend/src/App.css
git commit -m "style: refine phase 5 workbench layout"
```

---

### Task 5: Phase 5 Learning Document

**Files:**
- Create: `docs/learning/phase-05-frontend-workbench.md`

- [ ] **Step 1: Write the beginner-friendly learning document**

Create `docs/learning/phase-05-frontend-workbench.md` with these sections:

```markdown
# Phase 05: React Frontend Workbench

## 怎么使用这份文档
## 学习目标
## 业务背景
## 先建立最小前端概念
## 核心概念
## 设计原因
## 端到端流程
## 代码导读
## 边界条件
## 测试说明
## 排错手册
## 面试讲法
## 学习检查清单
```

The document must explain React state, TypeScript types, API calls, SSE EventSource, HumanGate approve/reject, audit logs, and delivery panels for a near-zero-background reader.

- [ ] **Step 2: Verify document markers and headings**

Run:

```powershell
Select-String -Path docs\learning\phase-05-frontend-workbench.md -Pattern 'T[B]D','TO[D]O','implement late[r]','fill in detail[s]'
Select-String -Path docs\learning\phase-05-frontend-workbench.md -Pattern '^## '
```

Expected: no marker matches, and all planned headings are present.

- [ ] **Step 3: Commit learning document**

Run:

```powershell
git add docs\learning\phase-05-frontend-workbench.md
git commit -m "docs: add phase 5 learning guide"
```

---

### Task 6: Final Verification And Completion Review

**Files:**
- All Phase 5 files.

- [ ] **Step 1: Run full backend verification**

Run:

```powershell
cd backend-java
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 2: Run full frontend verification**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: all frontend tests pass and production build succeeds.

- [ ] **Step 3: Check formatting and repository state**

Run:

```powershell
git diff --check
git status --short
git log --oneline --decorate -8
```

Expected: no whitespace errors. `git status --short` is empty after all commits.

- [ ] **Step 4: Verify acceptance criteria against current files**

Inspect:

```powershell
Get-Content -Raw frontend\src\App.tsx
Get-Content -Raw frontend\src\types.ts
Get-Content -Raw frontend\src\api.ts
Get-Content -Raw backend-java\src\main\java\com\xiaoc\workbench\project\api\ProjectStateResponse.java
Get-Content -Raw docs\learning\phase-05-frontend-workbench.md
```

Confirm:

- main screen is the workbench;
- TypeScript IDs are strings;
- approve and reject exist;
- SSE listens to Phase 4 event names;
- audit logs are queried;
- delivery panel has artifact, reflection, lessons;
- learning guide exists.

- [ ] **Step 5: Stop or ignore visual companion session**

The browser companion session lives under `.superpowers/brainstorm/` and is ignored by git. If it is still running, stop the Node process listening on port `61621`:

```powershell
Get-Process node | Where-Object { $_.Path -like '*node.exe' } | Select-Object Id,ProcessName,Path
Stop-Process -Id <visual-companion-node-pid>
```

Only stop the process that serves the brainstorm companion.
