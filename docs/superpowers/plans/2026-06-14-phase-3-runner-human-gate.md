# Phase 3 Runner HumanGate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Phase 3 backend execution for local queued run start, deterministic DAG runner progression, HumanGate approve/reject, and a beginner-friendly learning guide.

**Architecture:** Controllers call a `RunQueue` abstraction for run start. The first implementation is `LocalRunQueue`, which delegates immediately to `RunnerService` inside the same JVM. `RunnerService` owns the DAG state machine and repository updates; `ProjectApplicationService` remains responsible for assembling `ProjectStateResponse`.

**Tech Stack:** Java 21, Spring Boot 3.3.6, Spring MVC, Spring Data JPA, PostgreSQL/Flyway, JUnit 5, AssertJ, MockMvc.

---

## File Structure

- Modify: `backend-java/src/main/java/com/xiaoc/workbench/project/domain/Project.java`
  - Add status transition methods.
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/domain/OrchestratorRun.java`
  - Add status transition methods.
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/domain/OrchestratorTask.java`
  - Add status, output, and log transition methods.
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/domain/HumanGate.java`
  - Add approve and reject methods.
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/repository/OrchestratorTaskRepository.java`
  - Add `Optional<OrchestratorTask> findByRunIdAndNodeId(String runId, String nodeId)`.
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/repository/HumanGateRepository.java`
  - Add `Optional<HumanGate> findByTaskId(String taskId)` and `Optional<HumanGate> findFirstByRunIdOrderByCreatedAtDesc(String runId)`.
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/project/repository/ProjectRepository.java`
  - Add `Optional<Project> findById(String id)` is already inherited; no method needed unless project lookup by run is moved here.
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java`
  - Make state assembly reusable by run ID and include current HumanGate summary.
- Create: `backend-java/src/main/java/com/xiaoc/workbench/common/web/InvalidStateException.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/web/ApiExceptionHandler.java`
  - Map `InvalidStateException` to HTTP 409.
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/DeterministicTaskExecutor.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/RunnerService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunQueue.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/LocalRunQueue.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/RunController.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/HumanGateController.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/HumanGateDtos.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java`
  - Permit local demo run and gate endpoints.
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/service/RunnerServiceTest.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/api/RunControllerTest.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/api/HumanGateControllerTest.java`
- Modify tests if needed: `backend-java/src/test/java/com/xiaoc/workbench/project/service/ProjectApplicationServiceTest.java`
- Create: `docs/learning/phase-03-runner-human-gate.md`

---

### Task 1: Entity State Transitions and Repository Lookups

**Files:**
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/project/domain/Project.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/domain/OrchestratorRun.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/domain/OrchestratorTask.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/domain/HumanGate.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/repository/OrchestratorTaskRepository.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/repository/HumanGateRepository.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/repository/OrchestratorRepositoryTest.java`

- [ ] **Step 1: Write failing repository/entity behavior tests**

Add this test to `OrchestratorRepositoryTest`:

```java
@Test
void updatesRunTaskAndHumanGateStatuses() {
    Project project = projectRepository.save(
            new Project("project-phase3-state", "state transition check", "TASKS", "CREATED"));
    OrchestratorRun run = runRepository.save(
            new OrchestratorRun("run-phase3-state", project.getId(), "credit_card_installment_campaign_v1", "CREATED"));
    OrchestratorTask task = taskRepository.save(
            new OrchestratorTask(
                    "task-phase3-state",
                    run.getId(),
                    "human_gate_prd",
                    "PRD scope confirmation",
                    "HUMAN_GATE",
                    "USER",
                    "READY",
                    "",
                    "",
                    1));
    HumanGate gate = humanGateRepository.save(
            new HumanGate("gate-phase3-state", run.getId(), task.getId(), "WAITING", "Confirm PRD scope."));

    project.markRunning();
    run.markRunning();
    task.markWaitingHuman("waiting for PRD confirmation");
    gate.approve("scope confirmed", "local-user");
    task.complete("approved by local-user", "human gate approved");
    project.markCompleted();
    run.markCompleted();

    assertThat(projectRepository.saveAndFlush(project).getStatus()).isEqualTo("COMPLETED");
    assertThat(runRepository.saveAndFlush(run).getStatus()).isEqualTo("COMPLETED");
    assertThat(taskRepository.saveAndFlush(task).getStatus()).isEqualTo("COMPLETED");
    HumanGate loadedGate = humanGateRepository.saveAndFlush(gate);
    assertThat(loadedGate.getStatus()).isEqualTo("APPROVED");
    assertThat(loadedGate.getDecisionReason()).isEqualTo("scope confirmed");
    assertThat(loadedGate.getDecidedBy()).isEqualTo("local-user");
    assertThat(loadedGate.getDecidedAt()).isNotNull();
    assertThat(taskRepository.findByRunIdAndNodeId(run.getId(), "human_gate_prd")).contains(task);
    assertThat(humanGateRepository.findByTaskId(task.getId())).contains(loadedGate);
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=OrchestratorRepositoryTest#updatesRunTaskAndHumanGateStatuses test
```

Expected: compilation fails because transition methods and repository lookups do not exist.

- [ ] **Step 3: Implement minimal domain methods**

Add these methods to `Project`:

```java
public void markRunning() { this.status = "RUNNING"; }
public void markWaitingHuman() { this.status = "WAITING_HUMAN"; }
public void markCompleted() { this.status = "COMPLETED"; }
public void markFailed() { this.status = "FAILED"; }
```

Add the same four methods to `OrchestratorRun`.

Add these methods to `OrchestratorTask`:

```java
public void markReady() { this.status = "READY"; }
public void markRunning() { this.status = "RUNNING"; }
public void markWaitingHuman(String log) {
    this.status = "WAITING_HUMAN";
    this.log = log;
}
public void complete(String output, String log) {
    this.status = "COMPLETED";
    this.output = output;
    this.log = log;
}
public void fail(String log) {
    this.status = "FAILED";
    this.log = log;
}
```

Add these methods to `HumanGate`:

```java
public void approve(String reason, String decidedBy) {
    this.status = "APPROVED";
    this.decisionReason = reason;
    this.decidedBy = decidedBy;
    this.decidedAt = Instant.now();
}

public void reject(String reason, String decidedBy) {
    this.status = "REJECTED";
    this.decisionReason = reason;
    this.decidedBy = decidedBy;
    this.decidedAt = Instant.now();
}
```

Add repository methods:

```java
Optional<OrchestratorTask> findByRunIdAndNodeId(String runId, String nodeId);
```

```java
Optional<HumanGate> findByTaskId(String taskId);
Optional<HumanGate> findFirstByRunIdOrderByCreatedAtDesc(String runId);
```

- [ ] **Step 4: Run test to verify GREEN**

Run:

```powershell
cd backend-java
mvn -Dtest=OrchestratorRepositoryTest#updatesRunTaskAndHumanGateStatuses test
```

Expected: selected test passes.

---

### Task 2: ProjectState Includes HumanGate

**Files:**
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java`
- Modify: `backend-java/src/test/java/com/xiaoc/workbench/project/service/ProjectApplicationServiceTest.java`

- [ ] **Step 1: Write failing state assembly test**

Add this test to `ProjectApplicationServiceTest`:

```java
@Autowired
private HumanGateRepository humanGateRepository;

@Autowired
private OrchestratorTaskRepository taskRepository;

@Test
void returnsWaitingHumanGateInProjectState() {
    seeder.seedBuiltinAgents();
    ProjectStateResponse created = service.createProject("credit card installment campaign approval workflow");
    TaskSummary gateTask = created.tasks().stream()
            .filter(task -> task.kind().equals("human_gate"))
            .findFirst()
            .orElseThrow();
    humanGateRepository.save(new HumanGate(
            "gate-project-state",
            created.run().id(),
            gateTask.id(),
            "WAITING",
            "Confirm PRD scope before risk review."));

    ProjectStateResponse loaded = service.getProject(created.project().id());

    assertThat(loaded.humanGate()).isNotNull();
    assertThat(loaded.humanGate().id()).isEqualTo("gate-project-state");
    assertThat(loaded.humanGate().status()).isEqualTo("waiting");
    assertThat(loaded.humanGate().prompt()).contains("Confirm PRD scope");
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=ProjectApplicationServiceTest#returnsWaitingHumanGateInProjectState test
```

Expected: assertion fails because `humanGate()` is still null.

- [ ] **Step 3: Add reusable state assembly**

Update `ProjectApplicationService` constructor to accept `HumanGateRepository`.

Add:

```java
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
```

Inside `assembleState`, compute:

```java
HumanGateSummary humanGate = humanGateRepository.findFirstByRunIdOrderByCreatedAtDesc(run.getId())
        .map(this::toHumanGateSummary)
        .orElse(null);
```

Return that `humanGate` instead of null.

Add:

```java
private HumanGateSummary toHumanGateSummary(HumanGate gate) {
    return new HumanGateSummary(
            gate.getId(),
            gate.getRunId(),
            gate.getTaskId(),
            lower(gate.getStatus()),
            gate.getPrompt());
}
```

- [ ] **Step 4: Run test to verify GREEN**

Run:

```powershell
cd backend-java
mvn -Dtest=ProjectApplicationServiceTest#returnsWaitingHumanGateInProjectState test
```

Expected: selected test passes.

---

### Task 3: Deterministic Executor and Runner Service

**Files:**
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/DeterministicTaskExecutor.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/RunnerService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/common/web/InvalidStateException.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/web/ApiExceptionHandler.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/service/RunnerServiceTest.java`

- [ ] **Step 1: Write failing runner tests**

Create `RunnerServiceTest`:

```java
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
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=RunnerServiceTest test
```

Expected: compilation fails because runner classes do not exist.

- [ ] **Step 3: Implement deterministic executor**

Create:

```java
@Service
public class DeterministicTaskExecutor {
    public String execute(OrchestratorTask task) {
        return switch (task.getKind()) {
            case "LLM_PRD_DRAFT" -> "PRD draft for " + task.getName() + ": scope, roles, risks, and acceptance criteria.";
            case "LLM_RISK_REVIEW" -> "risk review for " + task.getName() + ": compliance boundaries and audit notes.";
            case "LLM_TECH_DESIGN" -> "technical design for " + task.getName() + ": API, state machine, and persistence plan.";
            case "LLM_TEST_PLAN" -> "test plan for " + task.getName() + ": unit, integration, and acceptance coverage.";
            case "DELIVERY_SUMMARY" -> "delivery summary for " + task.getName() + ": completed outputs and remaining checks.";
            case "REFLECTION" -> "reflection for " + task.getName() + ": what worked, what to improve, and reusable lessons.";
            case "LESSONS_EXTRACT" -> "lessons for " + task.getName() + ": reusable workflow and interview talking points.";
            default -> "deterministic output for " + task.getKind() + " task " + task.getName() + ".";
        };
    }
}
```

- [ ] **Step 4: Implement runner service**

`RunnerService` must:

- load run/project/tasks/edges;
- mark run/project running;
- loop ready tasks in sort order;
- execute non-human tasks;
- create or reuse a waiting gate for human tasks;
- unlock downstream tasks whose dependencies are complete;
- finish or fail the run.

Public methods:

```java
@Transactional
public ProjectStateResponse startRun(String runId)

@Transactional
public ProjectStateResponse approveGate(String gateId, String reason, String decidedBy)

@Transactional
public ProjectStateResponse rejectGate(String gateId, String reason, String decidedBy)
```

Use:

```java
private boolean dependenciesComplete(OrchestratorTask task, Map<String, OrchestratorTask> tasksByNodeId, List<TaskEdge> edges) {
    return edges.stream()
            .filter(edge -> edge.getTargetNodeId().equals(task.getNodeId()))
            .map(TaskEdge::getSourceNodeId)
            .allMatch(source -> "COMPLETED".equals(tasksByNodeId.get(source).getStatus()));
}
```

Use an id helper:

```java
private String id(String prefix) {
    return prefix + "-" + UUID.randomUUID();
}
```

Create `InvalidStateException`:

```java
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) {
        super(message);
    }
}
```

Add a 409 handler in `ApiExceptionHandler`:

```java
@ExceptionHandler(InvalidStateException.class)
ResponseEntity<Map<String, String>> handleInvalidState(InvalidStateException exception) {
    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of("error", "invalid_state", "message", exception.getMessage()));
}
```

- [ ] **Step 5: Run runner tests to verify GREEN**

Run:

```powershell
cd backend-java
mvn -Dtest=RunnerServiceTest test
```

Expected: four runner tests pass.

---

### Task 4: RunQueue and Run Start API

**Files:**
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunQueue.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/LocalRunQueue.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/RunController.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/api/RunControllerTest.java`

- [ ] **Step 1: Write failing controller test**

Create:

```java
@WebMvcTest(RunController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class RunControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private RunQueue runQueue;

    @Test
    void startsRunWithoutAuthenticationForLocalDemo() throws Exception {
        when(runQueue.enqueueStart("run-1")).thenReturn(phase3State("waiting_human", "waiting"));

        mockMvc.perform(post("/api/runs/run-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.status").value("waiting_human"))
                .andExpect(jsonPath("$.human_gate.status").value("waiting"));
    }

    private static ProjectStateResponse phase3State(String runStatus, String gateStatus) {
        return new ProjectStateResponse(
                new ProjectSummary("project-1", "goal", "tasks", runStatus),
                new RoomSummary("room-1", "project-1", "room"),
                List.of(),
                new RunSummary("run-1", "project-1", "credit_card_installment_campaign_v1", runStatus),
                List.of(),
                new HumanGateSummary("gate-1", "run-1", "task-2", gateStatus, "Confirm scope."),
                null,
                null);
    }
}
```

- [ ] **Step 2: Run controller test to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=RunControllerTest test
```

Expected: compilation fails because queue and controller classes do not exist.

- [ ] **Step 3: Implement queue and controller**

Create:

```java
public interface RunQueue {
    ProjectStateResponse enqueueStart(String runId);
}
```

Create:

```java
@Service
public class LocalRunQueue implements RunQueue {
    private final RunnerService runnerService;

    public LocalRunQueue(RunnerService runnerService) {
        this.runnerService = runnerService;
    }

    @Override
    public ProjectStateResponse enqueueStart(String runId) {
        return runnerService.startRun(runId);
    }
}
```

Create:

```java
@RestController
@RequestMapping("/api/runs")
public class RunController {
    private final RunQueue runQueue;

    public RunController(RunQueue runQueue) {
        this.runQueue = runQueue;
    }

    @PostMapping("/{runId}/start")
    public ProjectStateResponse startRun(@PathVariable String runId) {
        return runQueue.enqueueStart(runId);
    }
}
```

Permit:

```java
.requestMatchers(HttpMethod.POST, "/api/runs/*/start").permitAll()
```

- [ ] **Step 4: Run controller test to verify GREEN**

Run:

```powershell
cd backend-java
mvn -Dtest=RunControllerTest test
```

Expected: run controller test passes.

---

### Task 5: HumanGate Decision API

**Files:**
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/HumanGateDtos.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/HumanGateController.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/api/HumanGateControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Create:

```java
@WebMvcTest(HumanGateController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class HumanGateControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private RunnerService runnerService;

    @Test
    void approvesGateWithoutAuthenticationForLocalDemo() throws Exception {
        when(runnerService.approveGate("gate-1", "scope confirmed", "local-user"))
                .thenReturn(phase3State("completed", "approved"));

        mockMvc.perform(post("/api/human-gates/gate-1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"scope confirmed\",\"decided_by\":\"local-user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.status").value("completed"))
                .andExpect(jsonPath("$.human_gate.status").value("approved"));
    }

    @Test
    void rejectsGateWithoutAuthenticationForLocalDemo() throws Exception {
        when(runnerService.rejectGate("gate-1", "scope too broad", "local-user"))
                .thenReturn(phase3State("failed", "rejected"));

        mockMvc.perform(post("/api/human-gates/gate-1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"scope too broad\",\"decided_by\":\"local-user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.status").value("failed"))
                .andExpect(jsonPath("$.human_gate.status").value("rejected"));
    }

    @Test
    void mapsInvalidGateStateToConflict() throws Exception {
        when(runnerService.approveGate("gate-1", "scope confirmed", "local-user"))
                .thenThrow(new InvalidStateException("Gate was rejected"));

        mockMvc.perform(post("/api/human-gates/gate-1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"scope confirmed\",\"decided_by\":\"local-user\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("invalid_state"));
    }

    private static ProjectStateResponse phase3State(String runStatus, String gateStatus) {
        return new ProjectStateResponse(
                new ProjectSummary("project-1", "goal", "tasks", runStatus),
                new RoomSummary("room-1", "project-1", "room"),
                List.of(),
                new RunSummary("run-1", "project-1", "credit_card_installment_campaign_v1", runStatus),
                List.of(),
                new HumanGateSummary("gate-1", "run-1", "task-2", gateStatus, "Confirm scope."),
                null,
                null);
    }
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=HumanGateControllerTest test
```

Expected: compilation fails because gate API classes do not exist.

- [ ] **Step 3: Implement gate decision DTO and controller**

Create:

```java
record HumanGateDecisionRequest(
        @NotBlank String reason,
        @JsonProperty("decided_by") String decidedBy
) {
    String effectiveDecidedBy() {
        return decidedBy == null || decidedBy.isBlank() ? "local-user" : decidedBy.strip();
    }
}
```

Create:

```java
@RestController
@RequestMapping("/api/human-gates")
public class HumanGateController {
    private final RunnerService runnerService;

    public HumanGateController(RunnerService runnerService) {
        this.runnerService = runnerService;
    }

    @PostMapping("/{gateId}/approve")
    public ProjectStateResponse approve(@PathVariable String gateId, @Valid @RequestBody HumanGateDecisionRequest request) {
        return runnerService.approveGate(gateId, request.reason(), request.effectiveDecidedBy());
    }

    @PostMapping("/{gateId}/reject")
    public ProjectStateResponse reject(@PathVariable String gateId, @Valid @RequestBody HumanGateDecisionRequest request) {
        return runnerService.rejectGate(gateId, request.reason(), request.effectiveDecidedBy());
    }
}
```

Permit:

```java
.requestMatchers(HttpMethod.POST, "/api/human-gates/*/approve").permitAll()
.requestMatchers(HttpMethod.POST, "/api/human-gates/*/reject").permitAll()
```

- [ ] **Step 4: Run gate controller tests to verify GREEN**

Run:

```powershell
cd backend-java
mvn -Dtest=HumanGateControllerTest test
```

Expected: gate controller tests pass.

---

### Task 6: Phase 3 Learning Document

**Files:**
- Create: `docs/learning/phase-03-runner-human-gate.md`
- Modify: `docs/learning/README.md` only if a link or note is needed.

- [ ] **Step 1: Write the document**

The document must begin:

```markdown
# Phase 03: Runner + HumanGate

## 怎么使用这份文档
## 学习目标
## 业务背景
## 先建立最小后端概念
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

The document must teach:

- Runner;
- queue abstraction;
- local queue versus RabbitMQ;
- DAG dependency unlocking;
- state machine;
- deterministic executor;
- HumanGate waiting, approve, and reject;
- commands for `cd backend-java` and `mvn test`.

- [ ] **Step 2: Scan the document**

Run:

```powershell
Select-String -Path .\docs\learning\phase-03-runner-human-gate.md -Pattern '<placeholder-marker-1>','<placeholder-marker-2>'
```

Expected: no matches.

---

### Task 7: Full Verification and Commit

**Files:**
- Verify all files changed by Tasks 1-6.

- [ ] **Step 1: Run targeted tests**

Run:

```powershell
cd backend-java
mvn -Dtest=OrchestratorRepositoryTest#updatesRunTaskAndHumanGateStatuses,ProjectApplicationServiceTest#returnsWaitingHumanGateInProjectState,RunnerServiceTest,RunControllerTest,HumanGateControllerTest test
```

Expected: selected tests pass.

- [ ] **Step 2: Run full backend test suite**

Run:

```powershell
cd backend-java
mvn test
```

Expected: build success with all tests passing.

- [ ] **Step 3: Run repository checks**

Run:

```powershell
git status --short
git diff --check
git diff --stat
```

Expected: only Phase 3 files changed, no whitespace errors.

- [ ] **Step 4: Commit implementation**

Run:

```powershell
git add backend-java docs/learning/phase-03-runner-human-gate.md docs/superpowers/plans/2026-06-14-phase-3-runner-human-gate.md
git commit -m "feat: implement phase 3 runner human gate"
```

Expected: one implementation commit after the design commit.

---

## Self-Review Checklist

- Spec coverage: queue abstraction, local queue, runner state machine, HumanGate decisions, API, tests, and learning document are covered.
- Phase boundary: RabbitMQ, SSE, RuntimeEvent, AuditLog, real LLM, Runtime Daemon, Artifact, Reflection, and Lessons generation are excluded.
- TDD: production behavior starts from failing tests.
- DTO casing: persisted statuses remain uppercase; API status values remain lowercase.
- Verification: full backend `mvn test` is required before completion.
