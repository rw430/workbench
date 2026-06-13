# Phase 2 Intent Agent Project Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the strict Phase 2 backend slice for deterministic intent analysis, agent recommendation, DAG template loading, Project / Room / Run / Task / Edge creation, and ProjectState responses.

**Architecture:** Keep orchestration rules in services, not controllers. Persist existing uppercase domain values, then map API DTO values to lowercase snake_case for the current React client.

**Tech Stack:** Java 21, Spring Boot 3.3.6, Spring MVC, Spring Data JPA, Flyway/PostgreSQL, JUnit 5, AssertJ, MockMvc, SnakeYAML.

---

## File Structure

- Modify: `backend-java/pom.xml`
  - Add explicit SnakeYAML dependency managed by Spring Boot.
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java`
  - Permit Phase 2 demo endpoints.
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/agent/service/BuiltinAgentSeeder.java`
  - Run seed data at application startup while preserving explicit test usage.
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/repository/OrchestratorRunRepository.java`
  - Add project lookup for state reconstruction.
- Create: `backend-java/src/main/java/com/xiaoc/workbench/common/web/ApiExceptionHandler.java`
  - Map validation and not-found errors to clear JSON responses.
- Create: `backend-java/src/main/java/com/xiaoc/workbench/intent/api/IntentController.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/intent/api/IntentDtos.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/intent/service/IntentAnalysis.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/intent/service/IntentAnalysisService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/agent/api/AgentController.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/agent/api/AgentDtos.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/agent/service/AgentRecommendationService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/template/DagTemplate.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/template/DagTemplateLoader.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectController.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectDtos.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java`
- Create tests under matching `backend-java/src/test/java/com/xiaoc/workbench/...` packages.
- Create: `docs/learning/phase-02-intent-agent-project.md`

---

### Task 1: Intent Analysis Slice

**Files:**
- Create: `backend-java/src/main/java/com/xiaoc/workbench/intent/service/IntentAnalysis.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/intent/service/IntentAnalysisService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/intent/api/IntentDtos.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/intent/api/IntentController.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/intent/service/IntentAnalysisServiceTest.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/intent/api/IntentControllerTest.java`

- [ ] **Step 1: Write failing service tests**

```java
class IntentAnalysisServiceTest {
    private final IntentAnalysisService service = new IntentAnalysisService();

    @Test
    void recognizesCreditCardInstallmentGoldenPath() {
        IntentAnalysis analysis = service.analyze("请帮我设计一个银行信用卡分期活动配置与审批系统的研发方案，要求支持活动规则配置、审批流、风险校验、灰度发布和操作审计。");

        assertThat(analysis.mode()).isEqualTo("TASKS");
        assertThat(analysis.templateId()).isEqualTo("credit_card_installment_campaign_v1");
        assertThat(analysis.domain()).isEqualTo("banking_credit_card");
        assertThat(analysis.riskLevel()).isEqualTo("MEDIUM");
        assertThat(analysis.humanGateRequired()).isTrue();
        assertThat(analysis.candidateRoles()).containsExactly("PD", "DEV", "QA", "RISK", "PMO");
        assertThat(analysis.confidence()).isGreaterThanOrEqualTo(0.80);
    }

    @Test
    void fallsBackToGoldenTemplateForUnmatchedGoals() {
        IntentAnalysis analysis = service.analyze("整理一个通用研发任务");

        assertThat(analysis.templateId()).isEqualTo("credit_card_installment_campaign_v1");
        assertThat(analysis.confidence()).isLessThan(0.80);
        assertThat(analysis.candidateRoles()).containsExactly("PD", "DEV", "QA", "RISK", "PMO");
    }
}
```

- [ ] **Step 2: Run service tests to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=IntentAnalysisServiceTest test
```

Expected: compilation fails because `IntentAnalysisService` does not exist.

- [ ] **Step 3: Implement minimal intent service**

Implementation shape:

```java
public record IntentAnalysis(
    String mode,
    String templateId,
    String domain,
    String riskLevel,
    boolean humanGateRequired,
    double confidence,
    List<String> candidateRoles
) {}
```

```java
@Service
public class IntentAnalysisService {
    private static final List<String> ROLES = List.of("PD", "DEV", "QA", "RISK", "PMO");
    private static final List<String> GOLDEN_KEYWORDS = List.of("银行", "信用卡", "分期", "活动", "审批", "风险", "灰度", "审计");

    public IntentAnalysis analyze(String goal) {
        String normalized = goal == null ? "" : goal.strip();
        long hits = GOLDEN_KEYWORDS.stream().filter(normalized::contains).count();
        double confidence = hits >= 4 ? 0.92 : 0.55;
        return new IntentAnalysis("TASKS", "credit_card_installment_campaign_v1", "banking_credit_card", "MEDIUM", true, confidence, ROLES);
    }
}
```

- [ ] **Step 4: Run service tests to verify GREEN**

Run:

```powershell
cd backend-java
mvn -Dtest=IntentAnalysisServiceTest test
```

Expected: 2 tests pass.

- [ ] **Step 5: Write failing controller tests**

```java
@WebMvcTest(IntentController.class)
@Import({IntentAnalysisService.class, ApiExceptionHandler.class, SecurityConfig.class})
class IntentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void analyzesGoal() throws Exception {
        mockMvc.perform(post("/api/intent/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goal\":\"银行信用卡分期活动审批研发方案\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("tasks"))
            .andExpect(jsonPath("$.template_id").value("credit_card_installment_campaign_v1"))
            .andExpect(jsonPath("$.human_gate_required").value(true))
            .andExpect(jsonPath("$.candidate_roles[0]").value("PD"));
    }

    @Test
    void rejectsBlankGoal() throws Exception {
        mockMvc.perform(post("/api/intent/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goal\":\"  \"}"))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 6: Run controller tests to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=IntentControllerTest test
```

Expected: compilation fails because controller and DTO files do not exist.

- [ ] **Step 7: Implement controller DTO mapping**

Implementation shape:

```java
public record AnalyzeIntentRequest(@NotBlank String goal) {}
public record IntentAnalysisResponse(
    String mode,
    @JsonProperty("template_id") String templateId,
    String domain,
    @JsonProperty("risk_level") String riskLevel,
    @JsonProperty("human_gate_required") boolean humanGateRequired,
    double confidence,
    @JsonProperty("candidate_roles") List<String> candidateRoles
) {}
```

Controller maps uppercase persistence values to lowercase API strings with `Locale.ROOT`.

- [ ] **Step 8: Run Task 1 tests**

Run:

```powershell
cd backend-java
mvn -Dtest=IntentAnalysisServiceTest,IntentControllerTest test
```

Expected: all Task 1 tests pass.

---

### Task 2: Agent Recommendation Slice

**Files:**
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/agent/service/BuiltinAgentSeeder.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/agent/service/AgentRecommendationService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/agent/api/AgentDtos.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/agent/api/AgentController.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/agent/service/AgentRecommendationServiceTest.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/agent/api/AgentControllerTest.java`

- [ ] **Step 1: Write failing recommendation service test**

```java
@Import({BuiltinAgentSeeder.class, AgentRecommendationService.class, IntentAnalysisService.class})
class AgentRecommendationServiceTest extends PostgresIntegrationTest {
    @Autowired
    private BuiltinAgentSeeder seeder;
    @Autowired
    private IntentAnalysisService intentService;
    @Autowired
    private AgentRecommendationService recommendationService;

    @Test
    void recommendsEnabledAgentsInIntentRoleOrder() {
        seeder.seedBuiltinAgents();
        IntentAnalysis analysis = intentService.analyze("银行信用卡分期活动审批研发方案，包含风险校验和操作审计。");

        List<AgentSummary> agents = recommendationService.recommend(analysis);

        assertThat(agents).extracting(AgentSummary::role).containsExactly("PD", "DEV", "QA", "RISK", "PMO");
        assertThat(agents.get(0).skills()).contains("PRD", "需求分析");
        assertThat(agents.get(3).recommendationReason()).contains("风险边界");
    }
}
```

- [ ] **Step 2: Run service test to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=AgentRecommendationServiceTest test
```

Expected: compilation fails because `AgentRecommendationService` and `AgentSummary` do not exist.

- [ ] **Step 3: Implement recommendation service and startup seeding**

Implementation shape:

```java
public record AgentSummary(
    String id,
    String name,
    String role,
    List<String> skills,
    BigDecimal score,
    @JsonProperty("recommendation_reason") String recommendationReason
) {}
```

```java
@Service
public class AgentRecommendationService {
    public List<AgentSummary> recommend(IntentAnalysis analysis) {
        Map<String, Integer> roleOrder = IntStream.range(0, analysis.candidateRoles().size())
            .boxed()
            .collect(Collectors.toMap(analysis.candidateRoles()::get, index -> index));
        return repository.findAllByEnabledTrueOrderBySortOrderAsc().stream()
            .filter(agent -> roleOrder.containsKey(agent.getRole()))
            .sorted(Comparator.comparingInt(agent -> roleOrder.get(agent.getRole())))
            .map(this::toSummary)
            .toList();
    }
}
```

Modify `BuiltinAgentSeeder` to implement `ApplicationRunner`:

```java
@Override
public void run(ApplicationArguments args) {
    seedBuiltinAgents();
}
```

- [ ] **Step 4: Run recommendation service test to verify GREEN**

Run:

```powershell
cd backend-java
mvn -Dtest=AgentRecommendationServiceTest test
```

Expected: service test passes.

- [ ] **Step 5: Write failing controller tests**

```java
@WebMvcTest(AgentController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class AgentControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AgentRecommendationService recommendationService;
    @MockBean
    private IntentAnalysisService intentAnalysisService;

    @Test
    void listsAgents() throws Exception {
        when(recommendationService.listEnabled()).thenReturn(List.of(new AgentSummary("agent-pd-default", "需求分析分身", "PD", List.of("PRD"), new BigDecimal("0.9500"), "命中信用卡分期活动")));

        mockMvc.perform(get("/api/agents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].role").value("PD"))
            .andExpect(jsonPath("$[0].skills[0]").value("PRD"));
    }

    @Test
    void recommendsAgentsForGoal() throws Exception {
        IntentAnalysis analysis = new IntentAnalysis("TASKS", "credit_card_installment_campaign_v1", "banking_credit_card", "MEDIUM", true, 0.92, List.of("PD"));
        when(intentAnalysisService.analyze("银行信用卡分期活动")).thenReturn(analysis);
        when(recommendationService.recommend(analysis)).thenReturn(List.of(new AgentSummary("agent-pd-default", "需求分析分身", "PD", List.of("PRD"), new BigDecimal("0.9500"), "命中信用卡分期活动")));

        mockMvc.perform(post("/api/agents/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goal\":\"银行信用卡分期活动\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("agent-pd-default"));
    }
}
```

- [ ] **Step 6: Run controller tests to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=AgentControllerTest test
```

Expected: compilation fails because `AgentController` does not exist.

- [ ] **Step 7: Implement agent controller**

Controller endpoints:

```java
@GetMapping("/api/agents")
List<AgentSummary> listAgents()

@PostMapping("/api/agents/recommend")
List<AgentSummary> recommend(@Valid @RequestBody RecommendAgentsRequest request)
```

- [ ] **Step 8: Run Task 2 tests**

Run:

```powershell
cd backend-java
mvn -Dtest=AgentRecommendationServiceTest,AgentControllerTest,BuiltinAgentSeederTest test
```

Expected: all Task 2 tests pass.

---

### Task 3: DAG Template Loader

**Files:**
- Modify: `backend-java/pom.xml`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/template/DagTemplate.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/template/DagTemplateLoader.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/template/DagTemplateLoaderTest.java`

- [ ] **Step 1: Write failing loader tests**

```java
class DagTemplateLoaderTest {
    @Test
    void loadsCreditCardInstallmentTemplate() {
        DagTemplateLoader loader = new DagTemplateLoader("../templates/dags");

        DagTemplate template = loader.load("credit_card_installment_campaign_v1");

        assertThat(template.id()).isEqualTo("credit_card_installment_campaign_v1");
        assertThat(template.mode()).isEqualTo("tasks");
        assertThat(template.nodes()).extracting(DagTemplate.Node::id)
            .containsExactly("need_analysis", "human_gate_prd", "risk_compliance_review", "tech_design", "test_plan", "delivery_summary", "reflection", "lessons_extract");
        assertThat(template.nodes().get(1).dependsOn()).containsExactly("need_analysis");
    }

    @Test
    void rejectsMissingTemplate() {
        DagTemplateLoader loader = new DagTemplateLoader("../templates/dags");

        assertThatThrownBy(() -> loader.load("missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DAG template not found");
    }
}
```

- [ ] **Step 2: Run loader tests to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=DagTemplateLoaderTest test
```

Expected: compilation fails because loader classes do not exist.

- [ ] **Step 3: Add SnakeYAML dependency and implement loader**

Add to `pom.xml` dependencies:

```xml
<dependency>
  <groupId>org.yaml</groupId>
  <artifactId>snakeyaml</artifactId>
</dependency>
```

Implementation shape:

```java
public record DagTemplate(String id, String name, String mode, String domain, List<Node> nodes) {
    public record Node(String id, String name, String kind, String role, List<String> dependsOn) {}
}
```

`DagTemplateLoader` uses `Yaml.load(InputStream)` and validates duplicate node IDs plus unknown dependencies before returning an immutable `DagTemplate`.

- [ ] **Step 4: Run Task 3 tests**

Run:

```powershell
cd backend-java
mvn -Dtest=DagTemplateLoaderTest test
```

Expected: loader tests pass.

---

### Task 4: Project Application Service

**Files:**
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/repository/OrchestratorRunRepository.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectDtos.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/project/service/ProjectApplicationServiceTest.java`

- [ ] **Step 1: Write failing integration test**

```java
@Import({
    BuiltinAgentSeeder.class,
    IntentAnalysisService.class,
    AgentRecommendationService.class,
    DagTemplateLoader.class,
    ProjectApplicationService.class
})
class ProjectApplicationServiceTest extends PostgresIntegrationTest {
    @Autowired
    private BuiltinAgentSeeder seeder;
    @Autowired
    private ProjectApplicationService service;

    @Test
    void createsProjectStateFromGoldenPathTemplate() {
        seeder.seedBuiltinAgents();

        ProjectStateResponse state = service.createProject("银行信用卡分期活动审批研发方案，包含风险校验、灰度发布和操作审计。");

        assertThat(state.project().mode()).isEqualTo("tasks");
        assertThat(state.project().status()).isEqualTo("created");
        assertThat(state.room().name()).isEqualTo("信用卡分期活动研发协同室");
        assertThat(state.run().templateId()).isEqualTo("credit_card_installment_campaign_v1");
        assertThat(state.run().status()).isEqualTo("created");
        assertThat(state.agents()).extracting(AgentSummary::role).containsExactly("PD", "DEV", "QA", "RISK", "PMO");
        assertThat(state.tasks()).hasSize(8);
        assertThat(state.tasks().get(0).nodeId()).isEqualTo("need_analysis");
        assertThat(state.tasks().get(0).status()).isEqualTo("ready");
        assertThat(state.tasks().get(1).kind()).isEqualTo("human_gate");
        assertThat(state.tasks().get(1).dependsOn()).containsExactly("need_analysis");
        assertThat(state.humanGate()).isNull();
        assertThat(state.artifact()).isNull();
        assertThat(state.reflection()).isNull();
    }

    @Test
    void reconstructsProjectStateByProjectId() {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = service.createProject("银行信用卡分期活动审批研发方案");

        ProjectStateResponse loaded = service.getProject(created.project().id());

        assertThat(loaded.project().id()).isEqualTo(created.project().id());
        assertThat(loaded.tasks()).extracting(TaskSummary::nodeId)
            .containsExactlyElementsOf(created.tasks().stream().map(TaskSummary::nodeId).toList());
    }
}
```

- [ ] **Step 2: Run service test to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=ProjectApplicationServiceTest test
```

Expected: compilation fails because project DTOs and application service do not exist.

- [ ] **Step 3: Add repository lookup**

```java
public interface OrchestratorRunRepository extends JpaRepository<OrchestratorRun, String> {
    Optional<OrchestratorRun> findByProjectId(String projectId);
}
```

- [ ] **Step 4: Implement ProjectState DTOs**

Records include JSON annotations for snake_case fields:

```java
public record ProjectStateResponse(
    ProjectSummary project,
    RoomSummary room,
    List<AgentSummary> agents,
    RunSummary run,
    List<TaskSummary> tasks,
    @JsonProperty("human_gate") HumanGateSummary humanGate,
    String artifact,
    String reflection
) {}
```

`TaskSummary` exposes `depends_on` via `@JsonProperty("depends_on")`.

- [ ] **Step 5: Implement transactional application service**

Service behavior:

```java
@Transactional
public ProjectStateResponse createProject(String goal) {
    IntentAnalysis intent = intentAnalysisService.analyze(goal);
    DagTemplate template = dagTemplateLoader.load(intent.templateId());
    Project project = projectRepository.save(new Project(id("project"), goal.strip(), intent.mode(), "CREATED"));
    Room room = roomRepository.save(new Room(id("room"), project.getId(), "信用卡分期活动研发协同室"));
    OrchestratorRun run = runRepository.save(new OrchestratorRun(id("run"), project.getId(), template.id(), "CREATED"));
    saveTasks(run, template);
    saveEdges(run, template);
    return assembleState(project, room, run, agentRecommendationService.recommend(intent));
}
```

Root task status is `READY`; dependent task status is `PENDING`. DTO mapping lowercases values with `Locale.ROOT`.

- [ ] **Step 6: Run Task 4 tests**

Run:

```powershell
cd backend-java
mvn -Dtest=ProjectApplicationServiceTest test
```

Expected: service integration tests pass.

---

### Task 5: Project Controller, Security, and Error JSON

**Files:**
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/common/web/ApiExceptionHandler.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectController.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/project/api/ProjectControllerTest.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/common/security/SecurityConfigTest.java`

- [ ] **Step 1: Write failing controller test**

```java
@WebMvcTest(ProjectController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class ProjectControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ProjectApplicationService projectApplicationService;

    @Test
    void createsProjectWithoutAuthenticationForLocalDemo() throws Exception {
        ProjectStateResponse state = phase2State();
        when(projectApplicationService.createProject("银行信用卡分期活动审批研发方案")).thenReturn(state);

        mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goal\":\"银行信用卡分期活动审批研发方案\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.project.status").value("created"))
            .andExpect(jsonPath("$.run.template_id").value("credit_card_installment_campaign_v1"))
            .andExpect(jsonPath("$.tasks[1].depends_on[0]").value("need_analysis"))
            .andExpect(jsonPath("$.human_gate").value(nullValue()));
    }

    @Test
    void rejectsBlankProjectGoal() throws Exception {
        mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goal\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    private static ProjectStateResponse phase2State() {
        return new ProjectStateResponse(
            new ProjectSummary("project-1", "银行信用卡分期活动审批研发方案", "tasks", "created"),
            new RoomSummary("room-1", "project-1", "信用卡分期活动研发协同室"),
            List.of(new AgentSummary("agent-pd-default", "需求分析分身", "PD", List.of("PRD"), new BigDecimal("0.9500"), "命中信用卡分期活动")),
            new RunSummary("run-1", "project-1", "credit_card_installment_campaign_v1", "created"),
            List.of(
                new TaskSummary("task-1", "run-1", "need_analysis", "需求分析", "llm_prd_draft", "PD", List.of(), "ready", "", ""),
                new TaskSummary("task-2", "run-1", "human_gate_prd", "PRD 范围确认", "human_gate", "USER", List.of("need_analysis"), "pending", "", "")
            ),
            null,
            null,
            null
        );
    }
}
```

- [ ] **Step 2: Run controller test to verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=ProjectControllerTest test
```

Expected: compilation fails because controller and error handler do not exist.

- [ ] **Step 3: Implement project controller and error handler**

Controller endpoints:

```java
@PostMapping("/api/projects")
ProjectStateResponse createProject(@Valid @RequestBody CreateProjectRequest request)

@GetMapping("/api/projects/{projectId}")
ProjectStateResponse getProject(@PathVariable String projectId)
```

Error response shape:

```json
{
  "error": "validation_failed",
  "message": "goal must not be blank"
}
```

- [ ] **Step 4: Update security permit list**

Permit:

```java
.requestMatchers(
    "/api/health",
    "/api/intent/analyze",
    "/api/agents",
    "/api/agents/recommend",
    "/api/projects",
    "/api/projects/*",
    "/actuator/health"
).permitAll()
```

- [ ] **Step 5: Run controller/security tests**

Run:

```powershell
cd backend-java
mvn -Dtest=ProjectControllerTest,IntentControllerTest,AgentControllerTest,SecurityConfigTest test
```

Expected: controller and security tests pass.

---

### Task 6: Phase 2 Learning Document

**Files:**
- Create: `docs/learning/phase-02-intent-agent-project.md`

- [ ] **Step 1: Write the learning document**

The document must include:

```markdown
# Phase 02: Intent + Agent + Project 创建

## 学习目标
## 业务背景
## 核心概念
## 设计原因
## 端到端流程
## 代码导读
## 边界条件
## 测试说明
## 排错手册
## 面试讲法
```

The code guide must reference concrete Phase 2 files and the test command `cd backend-java; mvn test`.

- [ ] **Step 2: Review document for empty sections**

Run:

```powershell
Select-String -Path .\docs\learning\phase-02-intent-agent-project.md -Pattern 'T[B]D','TO[D]O','待[定]','稍[后]'
```

Expected: no matches.

---

### Task 7: Full Verification and Commit

**Files:**
- Verify all files changed by Tasks 1-6.

- [ ] **Step 1: Run full backend test suite**

Run:

```powershell
cd backend-java
mvn test
```

Expected: build success with all tests passing.

- [ ] **Step 2: Run git diff review**

Run:

```powershell
git status --short
git diff --check
git diff --stat
```

Expected: only Phase 2 files changed, no whitespace errors.

- [ ] **Step 3: Commit implementation**

Run:

```powershell
git add backend-java docs/learning/phase-02-intent-agent-project.md docs/superpowers/plans/2026-06-14-phase-2-intent-agent-project.md
git commit -m "feat: implement phase 2 intent agent project slice"
```

Expected: one implementation commit on the current branch.

---

## Self-Review Checklist

- Spec coverage: all Phase 2 bullets map to tasks.
- Phase boundary: Runner, HumanGate decisions, SSE, Audit, Artifact, Reflection, Lessons, Redis, RabbitMQ, and real LLM are excluded.
- TDD: each production code task starts with failing tests.
- DTO casing: persistence remains uppercase, API responses use lowercase snake_case.
- Verification: full backend `mvn test` is required before completion.
