# Phase 02: Intent + Agent + Project 创建

这份文档是给零基础读者看的。你不需要先懂 Spring Boot、JPA、数据库、HTTP、DTO、事务、测试，甚至不需要先懂什么是 AgentOps。我们会从最小概念开始，一步一步把 Phase 2 的代码串起来。

你可以把 Phase 2 想成一句话：

> 用户输入一句研发目标，后端把它变成一个可以保存、可以查询、可以继续执行的项目任务图。

这里的“继续执行”还不是 Phase 2 要做的事。Phase 2 只负责把项目、协作房间、运行实例、任务节点、任务依赖关系创建出来。真正让任务跑起来、推送 SSE、生成产物、做复盘，是后续阶段。

---

## 怎么使用这份文档

建议你按下面的顺序学，不要一开始就试图理解所有代码。

第一遍只看大意：知道用户请求从哪里进来，最后返回什么。

第二遍跟着“端到端流程”走一遍：从 `POST /api/projects` 进入，一直追到 `ProjectApplicationService.createProject`。

第三遍再看“代码导读”：每次只打开一个文件，理解它负责什么，不要同时打开十几个文件。

第四遍看测试：测试不是为了凑覆盖率，而是在告诉你“这段代码承诺了什么行为”。

学习时可以把自己放在这个角色里：

> 我是后端新手。我现在只需要知道一件事：当前这个文件负责接住什么输入，做什么处理，交给谁，最后返回什么。

如果你卡住了，先不要急着搜复杂概念。回到这四个问题：

1. 这个类是 Controller、Service、Repository、DTO，还是 Entity？
2. 它接收的数据从哪里来？
3. 它把数据传给谁？
4. 它返回的数据会被谁使用？

---

## 学习目标

学完 Phase 2，你应该能用自己的话讲清楚这些问题。

第一，什么是一个后端 API。比如 `POST /api/projects` 为什么能接收前端传来的 JSON，又为什么能返回一个 JSON。

第二，Controller、Service、Repository、DTO、Entity 分别是什么。你不需要背定义，但要知道它们在这个项目里的责任边界：

- Controller 负责接 HTTP 请求和返回 HTTP 响应。
- Service 负责业务流程，比如创建项目、加载 DAG、推荐 Agent。
- Repository 负责读写数据库。
- DTO 负责对外返回 JSON 结构。
- Entity 负责描述数据库里的表和字段。

第三，什么是 Intent、Agent、DAG、ProjectState。

- Intent 是“用户想做什么”的结构化结果。
- Agent 是系统推荐参与协作的角色，比如 PD、DEV、QA、RISK、PMO。
- DAG 是一张有方向、有依赖的任务图。
- ProjectState 是前端需要的一整包项目状态。

第四，能跟着一次 `POST /api/projects` 请求走完完整链路：

```text
HTTP 请求
  -> ProjectController
  -> ProjectApplicationService
  -> IntentAnalysisService
  -> DagTemplateLoader
  -> Repository 保存 Project / Room / Run / Task / Edge
  -> ProjectStateResponse
  -> HTTP JSON 响应
```

第五，能运行测试并大致看懂测试在证明什么：

```powershell
cd backend-java
mvn test
```

---

## 业务背景

这个项目做的是一个 AgentOps 工作台。名字听起来复杂，你可以先把它理解成“研发任务协作工作台”。

用户不会一开始就手动创建所有任务。他只会输入一句比较自然的话，例如：

```text
我要做一个银行信用卡分期活动配置和审批系统，需要支持规则配置、审批流、风险校验、灰度发布和操作审计。
```

如果是人工项目经理看到这句话，可能会做几件事：

1. 判断这是一个研发项目，不是聊天闲聊。
2. 判断它属于银行信用卡领域。
3. 判断需要产品、研发、测试、风控、项目管理等角色参与。
4. 拆出任务顺序，例如先做需求分析，再做 PRD 确认，再做风控评审，再做技术设计。
5. 把这些任务保存下来，后面才能执行、追踪、复盘。

Phase 2 就是把这些动作做成后端代码。

不过这里有一个重要边界：Phase 2 不调用真实大模型。原因很现实：如果每次演示都依赖真实 LLM，输出会不稳定，测试也不好写。所以这一阶段先用 deterministic 规则，也就是固定规则。只要输入里命中“银行、信用卡、分期、活动、审批、风险、灰度、审计”这些关键词，就认为它是黄金路径。

这个设计不是说以后不用 LLM，而是先把后端骨架搭稳。以后替换 `IntentAnalysisService` 的内部实现即可。

---

## 先建立最小后端概念

这一节只讲你读 Phase 2 必须知道的最少概念。

### HTTP 和 API

HTTP 是前端和后端沟通的一种规则。你在浏览器访问网页、前端调用后端接口，本质上都在发 HTTP 请求。

API 是后端暴露出来的入口。比如：

```text
POST /api/projects
GET /api/projects/{projectId}
POST /api/intent/analyze
GET /api/agents
```

`GET` 通常表示查询，`POST` 通常表示创建或提交数据。

### JSON

JSON 是前后端传数据的一种格式。比如创建项目时，前端传给后端：

```json
{
  "goal": "银行信用卡分期活动配置和审批系统"
}
```

后端返回的 `ProjectStateResponse` 也是 JSON，大概长这样：

```json
{
  "project": {
    "id": "project-...",
    "mode": "tasks",
    "status": "created"
  },
  "agents": [],
  "run": {
    "template_id": "credit_card_installment_campaign_v1"
  },
  "tasks": [],
  "human_gate": null,
  "artifact": null,
  "reflection": null
}
```

### Controller

Controller 是 HTTP 请求进来的第一站。

在 Phase 2 里，项目创建入口是：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectController.java
```

你看到：

```java
@PostMapping
public ProjectStateResponse createProject(@Valid @RequestBody CreateProjectRequest request) {
    return projectApplicationService.createProject(request.goal());
}
```

可以这样理解：

- `@PostMapping` 表示这是一个 POST 接口。
- `@RequestBody` 表示请求体里的 JSON 会被转成 Java 对象。
- `@Valid` 表示要做参数校验。
- Controller 不自己创建项目，只把目标交给 Service。

### Service

Service 是业务逻辑所在的位置。它负责“真正做事”。

Phase 2 最重要的 Service 是：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java
```

它做的事比较多：

- 分析用户目标。
- 加载 DAG 模板。
- 创建 Project。
- 创建 Room。
- 创建 Run。
- 创建 Task。
- 创建 TaskEdge。
- 组装 ProjectStateResponse。

### Repository

Repository 是读写数据库的入口。你可以先把它理解成“数据库助手”。

例如 `ProjectRepository` 负责保存和查询 `Project`。Service 不直接写 SQL，而是调用 Repository。

### Entity

Entity 是数据库表在 Java 里的样子。比如 Project、Room、OrchestratorRun、OrchestratorTask、TaskEdge 都是会落库的对象。

你可以把 Entity 理解成“数据库记录的 Java 版”。

### DTO

DTO 是 Data Transfer Object，意思是“用来传输数据的对象”。

数据库里怎么存，和 API 怎么返回，不一定完全一样。比如数据库里可能存大写：

```text
CREATED
READY
PENDING
```

但前端 API 想要小写：

```text
created
ready
pending
```

这时候 DTO 就负责给前端一个稳定、好用的 JSON 结构。

Phase 2 的主 DTO 是：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectStateResponse.java
```

### 事务

事务可以先理解成“一组数据库操作要么全部成功，要么全部失败”。

创建项目不是只插入一条记录。它要插入 Project、Room、Run、Task、Edge。如果中间某一步失败，最好不要留下半个项目。

所以 `ProjectApplicationService.createProject` 上有：

```java
@Transactional
```

这就是告诉 Spring：这整个方法是一组事务。

---

## 核心概念

### Intent：把一句话变成结构化意图

用户输入的是自然语言。代码不喜欢直接处理模糊自然语言，所以第一步要变成结构化结果。

结构化结果在这里叫 `IntentAnalysis`。它包含：

- `mode`: 当前固定为 `TASKS`，表示任务模式。
- `templateId`: 当前固定为 `credit_card_installment_campaign_v1`。
- `domain`: 当前是 `banking_credit_card`。
- `riskLevel`: 当前是 `MEDIUM`。
- `humanGateRequired`: 当前是 `true`。
- `confidence`: 命中关键词多时是 `0.92`，否则是 `0.55`。
- `candidateRoles`: 推荐角色顺序，PD、DEV、QA、RISK、PMO。

对应文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/intent/service/IntentAnalysis.java
backend-java/src/main/java/com/xiaoc/workbench/intent/service/IntentAnalysisService.java
```

### Agent：不是神秘 AI，而是一个协作角色

这里的 Agent 可以先理解成“一个擅长某类工作的协作角色”。

例如：

- PD 负责需求分析和 PRD。
- DEV 负责技术设计和实现建议。
- QA 负责测试计划和验收。
- RISK 负责风险合规评审。
- PMO 负责交付管理和复盘。

内置 Agent 数据由这个类提供：

```text
backend-java/src/main/java/com/xiaoc/workbench/agent/service/BuiltinAgentSeeder.java
```

推荐逻辑由这个类提供：

```text
backend-java/src/main/java/com/xiaoc/workbench/agent/service/AgentRecommendationService.java
```

### DAG：有依赖关系的任务图

DAG 是 Directed Acyclic Graph，中文可以叫“有向无环图”。

这四个字不用背。你只要先理解：

- 有向：任务有方向，从前一个任务指向后一个任务。
- 无环：不能绕回去，否则任务会无限循环。
- 图：不是单纯列表，节点之间有依赖。

Phase 2 的任务顺序在 YAML 模板里：

```text
templates/dags/credit_card_installment_campaign_v1.yaml
```

里面每个节点都有：

- `id`: 节点唯一标识。
- `name`: 节点展示名。
- `kind`: 节点类型。
- `role`: 负责角色。
- `depends_on`: 依赖哪些节点。

例如 `human_gate_prd` 依赖 `need_analysis`，意思是 PRD 范围确认必须等需求分析之后。

### ProjectState：一次返回给前端的完整状态

前端不希望创建项目后再调用十几个接口才能拼页面。它希望一次拿到当前项目状态。

所以 Phase 2 返回一个 `ProjectStateResponse`，里面有：

- `project`: 项目本身。
- `room`: 协作房间。
- `agents`: 推荐 Agent 列表。
- `run`: 本次编排运行实例。
- `tasks`: DAG 任务节点列表。
- `human_gate`: Phase 2 暂时为 `null`。
- `artifact`: Phase 2 暂时为 `null`。
- `reflection`: Phase 2 暂时为 `null`。

对应文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectStateResponse.java
```

---

## 设计原因

### 为什么先用固定规则，不直接接 LLM

新手容易觉得“Agent 项目当然要先接大模型”。但工程上通常不能这样急。

如果一开始就依赖真实 LLM，会出现几个问题：

- 每次输出可能不一样，测试不稳定。
- 网络、Key、模型额度都会影响演示。
- 后端数据库和任务图还没稳定，问题会混在一起，很难排查。

所以 Phase 2 先用固定规则。这样可以先证明：

```text
用户目标 -> Intent -> Agent 推荐 -> DAG 模板 -> ProjectState
```

这条链路是通的。以后只要替换 Intent 分析内部逻辑，就可以从固定规则升级成真实 LLM。

### 为什么 Controller 不直接创建数据库记录

Controller 的职责应该轻。它负责接请求、校验请求、返回响应。

如果把创建 Project、Room、Run、Task、Edge 的逻辑全写在 Controller 里，Controller 会变得很难读，也很难测试。

所以 Phase 2 把核心流程放在：

```text
ProjectApplicationService
```

这个类就是“应用服务”。它负责跨多个领域对象做编排。

### 为什么要有 Project、Room、Run、Task、Edge

这些对象看起来很多，但它们各自有自己的含义。

`Project` 是用户创建的项目。它保存目标、模式和状态。

`Room` 是协作房间。后续聊天、协作、消息都可以挂到这个房间。

`Run` 是一次编排运行。一个项目未来可能有多次运行，所以 Project 和 Run 分开。

`Task` 是具体任务节点，例如需求分析、风控评审、技术设计。

`Edge` 是任务依赖关系，例如 `human_gate_prd` 依赖 `need_analysis`。

### 为什么数据库用大写，API 返回小写

后端领域状态通常用大写，比较像枚举值：

```text
CREATED
READY
PENDING
```

前端 JSON 更常用小写：

```text
created
ready
pending
```

所以代码里有映射。比如 `ProjectApplicationService.lower` 会用 `Locale.ROOT` 转小写。

这能保证内部领域表达稳定，同时 API 对前端友好。

---

## 端到端流程

这一节我们跟着一次 `POST /api/projects` 请求走。

### 第 1 步：前端发请求

请求大概是：

```http
POST /api/projects
Content-Type: application/json
```

请求体：

```json
{
  "goal": "银行信用卡分期活动配置和审批系统，需要支持规则配置、审批流、风险校验、灰度发布和操作审计"
}
```

### 第 2 步：ProjectController 接住请求

打开：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectController.java
```

关键代码：

```java
@PostMapping
public ProjectStateResponse createProject(@Valid @RequestBody CreateProjectRequest request) {
    return projectApplicationService.createProject(request.goal());
}
```

这里发生三件事：

1. Spring 把 JSON 转成 `CreateProjectRequest`。
2. `@Valid` 检查 `goal` 是否为空。
3. Controller 把 `goal` 交给 `ProjectApplicationService`。

### 第 3 步：ProjectApplicationService 开始事务

打开：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java
```

关键入口：

```java
@Transactional
public ProjectStateResponse createProject(String goal) {
```

`@Transactional` 表示后面一串数据库操作在同一个事务里。

### 第 4 步：清理和校验 goal

代码先处理空值和空白：

```java
String trimmedGoal = goal == null ? "" : goal.strip();
if (trimmedGoal.isBlank()) {
    throw new IllegalArgumentException("goal must not be blank");
}
```

这一步很基础，但很重要。不要让空目标进入后面的业务流程。

### 第 5 步：分析 Intent

```java
IntentAnalysis intent = intentAnalysisService.analyze(trimmedGoal);
```

它会进入：

```text
backend-java/src/main/java/com/xiaoc/workbench/intent/service/IntentAnalysisService.java
```

当前实现会统计关键词命中数。如果命中足够多，`confidence` 就是 `0.92`；否则是 `0.55`。

### 第 6 步：加载 DAG 模板

```java
DagTemplate template = dagTemplateLoader.load(intent.templateId());
```

它会进入：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/template/DagTemplateLoader.java
```

模板文件在：

```text
templates/dags/credit_card_installment_campaign_v1.yaml
```

Loader 会检查：

- 模板文件是否存在。
- YAML 顶层是不是对象。
- 模板里的 `id` 是否等于请求的 `templateId`。
- 是否有重复节点。
- `depends_on` 是否引用了不存在的节点。

### 第 7 步：保存 Project、Room、Run

代码：

```java
Project project = projectRepository.save(new Project(id("project"), trimmedGoal, intent.mode(), "CREATED"));
Room room = roomRepository.save(new Room(id("room"), project.getId(), ROOM_NAME));
OrchestratorRun run = runRepository.save(
        new OrchestratorRun(id("run"), project.getId(), template.id(), "CREATED"));
```

这里先保存三个“主对象”：

- Project：项目。
- Room：协作房间。
- Run：编排运行。

### 第 8 步：保存 Task

```java
saveTasks(run, template);
```

每个 YAML 节点都会变成一个数据库里的 `OrchestratorTask`。

没有依赖的节点状态是：

```text
READY
```

有依赖的节点状态是：

```text
PENDING
```

这很符合直觉：第一个任务可以准备执行，后面的任务要等前置任务完成。

### 第 9 步：保存 Edge

```java
saveEdges(run, template);
```

Edge 记录的是依赖关系。

例如：

```text
need_analysis -> human_gate_prd
```

意思是 `human_gate_prd` 依赖 `need_analysis`。

注意：代码先保存 Task，再保存 Edge。这是因为 Edge 依赖任务节点存在。

### 第 10 步：组装响应

```java
return assembleState(project, room, run, agentRecommendationService.recommend(intent));
```

这一步会：

- 查询任务列表。
- 查询依赖关系。
- 推荐 Agent。
- 把大写状态转换成小写。
- 返回 `ProjectStateResponse`。

---

## 代码导读

这一节按文件读。建议你一次只打开一个文件。

### IntentAnalysisService.java

路径：

```text
backend-java/src/main/java/com/xiaoc/workbench/intent/service/IntentAnalysisService.java
```

这个类只做一件事：把用户目标变成 `IntentAnalysis`。

你先看这两行：

```java
private static final List<String> ROLES = List.of("PD", "DEV", "QA", "RISK", "PMO");
private static final List<String> GOLDEN_KEYWORDS = List.of(...);
```

`ROLES` 是候选角色顺序。`GOLDEN_KEYWORDS` 是黄金路径关键词。

再看：

```java
long hits = GOLDEN_KEYWORDS.stream().filter(normalized::contains).count();
double confidence = hits >= 4 ? 0.92 : 0.55;
```

意思是：命中关键词越多，系统越确信这是黄金路径。

检查点：如果用户输入完全不包含银行和信用卡，系统还会返回模板吗？

答案：会。只是 confidence 变低。这是为了保证 demo 链路稳定。

### IntentController.java

路径：

```text
backend-java/src/main/java/com/xiaoc/workbench/intent/api/IntentController.java
```

这个类暴露：

```text
POST /api/intent/analyze
```

它把 Service 返回的大写 `TASKS`、`MEDIUM` 转成 API 小写 `tasks`、`medium`。

你要记住一个原则：Controller 主要做输入输出适配，不做复杂业务。

### AgentRecommendationService.java

路径：

```text
backend-java/src/main/java/com/xiaoc/workbench/agent/service/AgentRecommendationService.java
```

它有两个主要方法：

```java
public List<AgentSummary> listEnabled()
public List<AgentSummary> recommend(IntentAnalysis analysis)
```

`listEnabled` 是列出所有启用 Agent。

`recommend` 是根据 Intent 里的 `candidateRoles` 调整返回顺序。

重点看这个逻辑：

```java
Map<String, Integer> roleOrder = IntStream.range(0, analysis.candidateRoles().size())
        .boxed()
        .collect(Collectors.toMap(analysis.candidateRoles()::get, index -> index));
```

这段代码把角色顺序变成一个 Map：

```text
PD -> 0
DEV -> 1
QA -> 2
RISK -> 3
PMO -> 4
```

后面排序时就可以按这个顺序来。

### BuiltinAgentSeeder.java

路径：

```text
backend-java/src/main/java/com/xiaoc/workbench/agent/service/BuiltinAgentSeeder.java
```

Seeder 的意思是“种子数据填充器”。项目启动时需要一些内置 Agent，否则推荐列表会为空。

它的关键行为是：

```java
if (!repository.existsById(agent.getId())) {
    repository.save(agent);
}
```

这保证重复执行不会插入重复数据。

启动时自动 seed 的配置在：

```text
backend-java/src/main/java/com/xiaoc/workbench/agent/service/BuiltinAgentConfiguration.java
```

这里用 `ObjectProvider<AgentProfileRepository>` 是为了兼容两种场景：

- 有 JPA 仓库时，启动后自动填充 Agent。
- 无 JPA 的 smoke test 中，不因为缺少仓库而启动失败。

### DagTemplateLoader.java

路径：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/template/DagTemplateLoader.java
```

它负责把 YAML 文件变成 Java 对象 `DagTemplate`。

你可以先只记住三步：

1. 找到模板文件。
2. 用 SnakeYAML 读取。
3. 校验节点和依赖。

为什么要校验？因为模板如果写错，后面创建任务图就会产生坏数据。越早报错，越容易排查。

### ProjectApplicationService.java

路径：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java
```

这是 Phase 2 最核心的文件。

如果你现在只能读懂一个文件，就读它。

建议按这个顺序看：

1. `createProject`
2. `saveTasks`
3. `saveEdges`
4. `assembleState`
5. `toTaskSummary`
6. `getProject`

`createProject` 是主流程。`saveTasks` 和 `saveEdges` 把 DAG 落库。`assembleState` 把数据库对象组装成前端需要的响应。

### ProjectController.java

路径：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectController.java
```

它提供两个接口：

```text
POST /api/projects
GET /api/projects/{projectId}
```

第一个创建项目。第二个按项目 ID 查询项目状态。

### ApiExceptionHandler.java

路径：

```text
backend-java/src/main/java/com/xiaoc/workbench/common/web/ApiExceptionHandler.java
```

它负责把异常变成清晰的 JSON 错误。

例如空 goal 会变成：

```json
{
  "error": "validation_failed",
  "message": "goal must not be blank"
}
```

项目不存在会变成：

```json
{
  "error": "not_found",
  "message": "Project not found: project-xxx"
}
```

---

## 边界条件

边界条件就是“正常路径之外会发生什么”。

### 空 goal

如果请求是：

```json
{
  "goal": "   "
}
```

Controller 的 `@NotBlank` 会拦截，返回 400。

如果绕过 Controller 直接调用 Service，`ProjectApplicationService` 里也会检查空白 goal。

这是双层保护。

### 未命中关键词

如果用户输入不是银行信用卡相关内容，系统仍然返回同一个模板，只是 confidence 低。

原因是 Phase 2 更重视链路稳定，不希望因为分类不准导致项目无法创建。

### 模板不存在

如果模板 ID 找不到，`DagTemplateLoader` 会抛：

```text
DAG template not found
```

这会阻止项目继续创建。

### 模板依赖写错

如果某个节点依赖了不存在的节点，Loader 会报 unknown dependency。

例如节点写了：

```yaml
depends_on: [missing_node]
```

但模板里没有 `missing_node`，这就是坏模板。

### Phase 2 不做什么

Phase 2 不做这些事：

- 不调用真实 LLM。
- 不执行 DAG。
- 不启动 Runner。
- 不推送 SSE。
- 不在运行时创建 HumanGate 决策。
- 不生成 Artifact。
- 不生成 Reflection。
- 不生成 Lessons。
- 不接 Redis。
- 不接 RabbitMQ。

如果你看到 `human_gate`、`artifact`、`reflection` 返回 `null`，这不是漏做，而是阶段边界。

---

## 测试说明

运行完整后端测试：

```powershell
cd backend-java
mvn test
```

### Intent 测试在证明什么

文件：

```text
backend-java/src/test/java/com/xiaoc/workbench/intent/service/IntentAnalysisServiceTest.java
backend-java/src/test/java/com/xiaoc/workbench/intent/api/IntentControllerTest.java
```

它们证明：

- 黄金路径输入会返回正确模板。
- 未命中输入会 fallback 到同一个模板。
- API 返回字段是小写和 snake_case。
- 空 goal 会被拒绝。

### Agent 测试在证明什么

文件：

```text
backend-java/src/test/java/com/xiaoc/workbench/agent/service/AgentRecommendationServiceTest.java
backend-java/src/test/java/com/xiaoc/workbench/agent/api/AgentControllerTest.java
backend-java/src/test/java/com/xiaoc/workbench/agent/service/BuiltinAgentStartupSeederTest.java
```

它们证明：

- 内置 Agent 能被填充。
- 推荐顺序符合 Intent 的 candidateRoles。
- skills 字符串能转换成数组。
- 应用启动时可以自动 seed。

### DAG 测试在证明什么

文件：

```text
backend-java/src/test/java/com/xiaoc/workbench/orchestrator/template/DagTemplateLoaderTest.java
```

它证明：

- YAML 模板能被加载。
- 节点顺序正确。
- 依赖关系正确。
- 缺失模板、重复节点、未知依赖会报错。

### Project 测试在证明什么

文件：

```text
backend-java/src/test/java/com/xiaoc/workbench/project/service/ProjectApplicationServiceTest.java
backend-java/src/test/java/com/xiaoc/workbench/project/api/ProjectControllerTest.java
```

它们证明：

- 创建项目会落库 Project、Room、Run、Task、Edge。
- 第一个任务是 `ready`。
- 有依赖的任务是 `pending`。
- `human_gate`、`artifact`、`reflection` 在 Phase 2 是 `null`。
- 可以按 projectId 重新组装 ProjectState。

---

## 排错手册

### 运行测试时提示找不到模板

先确认你在正确目录运行：

```powershell
cd backend-java
mvn test
```

`DagTemplateLoader` 默认会找：

```text
../templates/dags/credit_card_installment_campaign_v1.yaml
```

如果你在仓库根目录直接运行某些测试，路径可能不同。

### API 返回 401

401 表示未认证。

检查：

```text
backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java
```

Phase 2 demo 需要放开：

```text
POST /api/intent/analyze
GET /api/agents
POST /api/agents/recommend
POST /api/projects
GET /api/projects/*
```

### 空 goal 没有返回 validation_failed

检查两个地方：

1. DTO 上是否有 `@NotBlank`。
2. `ApiExceptionHandler` 是否处理了 `MethodArgumentNotValidException`。

### Agent 推荐为空

优先检查 seed 是否执行。

相关文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/agent/service/BuiltinAgentSeeder.java
backend-java/src/main/java/com/xiaoc/workbench/agent/service/BuiltinAgentConfiguration.java
```

如果数据库里没有内置 Agent，`AgentRecommendationService` 自然推荐不出结果。

### TaskEdge 外键失败

优先检查顺序：

```text
先 saveTasks
再 saveEdges
```

如果 Edge 先保存，但对应 Task 还不存在，数据库会拒绝。

Phase 2 里 `saveTasks` 使用 `saveAllAndFlush`，就是为了确保任务先落到数据库。

### API 返回大写 status

检查：

```text
ProjectApplicationService.lower
IntentController.analyze
```

API 应该返回：

```text
created
ready
pending
tasks
medium
```

而不是：

```text
CREATED
READY
PENDING
TASKS
MEDIUM
```

### 中文显示乱码

如果终端里看到乱码，先不要立刻判断文件坏了。Windows PowerShell、终端编码、文件编码可能不一致。

优先用编辑器打开 Markdown 文件确认。如果文件本身保存为 UTF-8，编辑器里应该能正常显示中文。

---

## 面试讲法

你可以把 Phase 2 讲成一条清晰主线：

> 这一阶段我实现的是从自然语言目标到可持久化项目任务图的后端链路。用户提交 goal 后，系统先做 deterministic intent 分析，再推荐协作 Agent，然后加载 DAG 模板，最后在一个事务里创建 Project、Room、Run、Task 和 Edge，并返回前端需要的 ProjectState。

如果面试官问“为什么不直接接大模型”，可以这样说：

> 我把真实 LLM 放到后续阶段，是为了先保证核心链路稳定可测。Phase 2 用固定规则模拟 intent provider，这样测试可重复，演示稳定。未来替换 IntentAnalysisService 的内部实现即可，不影响 Project 创建事务和 DAG 落库逻辑。

如果面试官问“为什么要用 DAG”，可以这样说：

> 因为研发任务不是简单列表，任务之间有前置依赖。比如 PRD 确认依赖需求分析，风控评审依赖 PRD 确认。DAG 可以明确表达这种依赖关系，后续 Runner 执行时也能根据依赖判断哪些任务可运行。

如果面试官问“事务解决了什么问题”，可以这样说：

> 创建项目会同时写 Project、Room、Run、Task、Edge。任何一步失败都不应该留下半成品，所以我把创建流程放在 ProjectApplicationService 的 @Transactional 方法里，保证这一组写入要么全部成功，要么失败回滚。

如果面试官问“Controller 和 Service 怎么分工”，可以这样说：

> Controller 只做 HTTP 输入输出适配，例如接收 JSON、触发校验、返回 DTO。真正的业务编排放在 Service 里，比如分析 intent、加载模板、保存任务图、组装 ProjectState。这样 Controller 更薄，Service 更容易测试。

如果面试官问“Phase 2 的边界是什么”，可以这样说：

> Phase 2 只负责实例化任务图，不负责执行任务图。因此它不做 Runner、不推 SSE、不创建运行时 HumanGate 决策、不生成 Artifact、Reflection 和 Lessons。这些属于后续阶段，基于 Phase 2 已经落库的 Run、Task、Edge 继续推进。

最后，你可以用一句话收束：

> 这个阶段的重点不是炫技，而是把项目从一句自然语言目标变成稳定、可查询、可继续执行的后端状态，为后续 Agent 执行、人工确认、实时事件和复盘沉淀打基础。

---

## 学习检查清单

学完后，你可以用下面的问题检查自己。

- 你能说出 Controller、Service、Repository、DTO、Entity 的区别吗？
- 你能解释为什么 `ProjectApplicationService.createProject` 要加 `@Transactional` 吗？
- 你能说出 `Project`、`Room`、`Run`、`Task`、`Edge` 分别代表什么吗？
- 你能画出 `POST /api/projects` 的调用链吗？
- 你能解释为什么 Phase 2 里 `human_gate` 是 `null` 吗？
- 你能运行 `cd backend-java; mvn test` 并知道大概有哪些测试在跑吗？

如果这些问题你都能用自己的话回答，Phase 2 的学习目标就达到了。
