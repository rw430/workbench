# Phase 02: Intent + Agent + Project 创建

## 学习目标

完成本阶段后，你应该能解释一个 AgentOps 工作台如何从用户目标进入可执行的研发协同状态：先识别 Intent，再推荐 Agent，随后加载 DAG 模板，并把 Project、Room、Run、Task、Edge 一次性落库。

你还应该能读懂本阶段的 Spring Boot 分层：Controller 负责 HTTP 请求和 DTO，Application Service 负责事务和跨领域编排，Domain Service 负责 deterministic 规则，Repository 负责持久化。

## 业务背景

黄金路径是“银行信用卡分期活动配置与审批系统研发协同”。工作台本身不处理真实银行交易，也不做真实信用审批。它接收一个研发需求，把需求转成协同研发任务图，让 PD、DEV、QA、RISK、PMO 等角色进入同一个可追踪的项目上下文。

Phase 2 的价值是把静态数据底座变成可调用的后端入口。前端或 API 调用方提交 goal 后，可以拿到完整的 `ProjectState`，其中包括推荐 Agent、运行实例、DAG 节点和依赖关系。

## 核心概念

`IntentAnalysis` 是用户目标的结构化结果。当前使用 deterministic fallback，不依赖真实 LLM。黄金路径命中银行、信用卡、分期、活动、审批、风险、灰度、审计等关键词时，会返回更高 confidence。

`AgentSummary` 是前端和项目服务消费的 Agent 推荐视图。它从 `AgentProfile` 映射而来，把逗号分隔的 `skills` 字段转换成数组。

`DagTemplate` 是 YAML 模板的内存模型。`credit_card_installment_campaign_v1.yaml` 定义了从 `need_analysis` 到 `lessons_extract` 的节点顺序和依赖关系。

`ProjectStateResponse` 是 Phase 2 的主要交付 DTO。它聚合 Project、Room、Agent Team、Run、Task 列表和 Phase 2 暂不生成的 HumanGate、Artifact、Reflection 字段。

## 设计原因

本阶段选择 deterministic intent 和模板加载，是为了保证面试演示稳定可复现。真实 LLM 可以在后续替换 `IntentAnalysisService`，但不应该影响 Project 创建事务。

Project 创建被放在 `ProjectApplicationService` 中，而不是 Controller 中，是因为它跨越多个领域对象：Project、Room、Run、Task、Edge 和 Agent 推荐。这个服务提供清晰的事务边界，避免半成品 DAG 落库。

持久化层继续使用大写语义值，例如 `TASKS`、`CREATED`、`READY`、`PENDING`、`HUMAN_GATE`。API DTO 返回小写 snake_case，例如 `tasks`、`created`、`ready`、`pending`、`human_gate`，以匹配当前 React 客户端。

## 端到端流程

1. 调用 `POST /api/intent/analyze`，`IntentAnalysisService` 返回 mode、template、domain、risk level 和 candidate roles。
2. 调用 `GET /api/agents` 或 `POST /api/agents/recommend`，`AgentRecommendationService` 读取 enabled Agent 并按角色顺序返回。
3. 调用 `POST /api/projects`，`ProjectController` 校验 goal 后进入 `ProjectApplicationService`。
4. `ProjectApplicationService` 分析 intent，加载 `credit_card_installment_campaign_v1` 模板，创建 Project、Room 和 Run。
5. 服务按模板顺序创建 Task。无依赖节点为 `READY`，有依赖节点为 `PENDING`。
6. 服务根据每个节点的 `depends_on` 创建 TaskEdge。
7. 服务组装 `ProjectStateResponse` 返回给调用方。Phase 2 中 `human_gate`、`artifact`、`reflection` 为 `null`。

## 代码导读

Intent 分析入口在 `backend-java/src/main/java/com/xiaoc/workbench/intent/service/IntentAnalysisService.java`。它保留固定模板和候选角色，只根据关键词命中数调整 confidence。

Intent API 在 `backend-java/src/main/java/com/xiaoc/workbench/intent/api/IntentController.java`，负责把服务层大写值转换为 API 小写值。

Agent 推荐在 `backend-java/src/main/java/com/xiaoc/workbench/agent/service/AgentRecommendationService.java`。它读取 `AgentProfileRepository`，过滤 intent candidate roles，并把 skills 转成列表。

Agent seed data 在 `backend-java/src/main/java/com/xiaoc/workbench/agent/service/BuiltinAgentSeeder.java`。它实现 `ApplicationRunner`，应用启动时自动补齐内置 Agent，同时保留显式 `seedBuiltinAgents()` 供测试使用。

DAG 模板加载在 `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/template/DagTemplateLoader.java`。它读取 `templates/dags/credit_card_installment_campaign_v1.yaml`，校验模板 ID、重复节点和未知依赖。

项目创建主流程在 `backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java`。重点看 `createProject`、`saveTasks`、`saveEdges` 和 `assembleState`。

Project API 在 `backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectController.java`，提供 `POST /api/projects` 和 `GET /api/projects/{projectId}`。

错误响应在 `backend-java/src/main/java/com/xiaoc/workbench/common/web/ApiExceptionHandler.java`。校验失败返回 `validation_failed`，项目不存在返回 `not_found`。

## 边界条件

空 goal 会被 Controller 的 `@NotBlank` 拦截并返回 400。

未命中黄金路径关键词时，Intent 仍然返回信用卡分期模板，但 confidence 降低。这保证本地演示不被 LLM 或分类失败阻断。

DAG 模板缺失、模板 ID 不匹配、重复 node ID 或未知依赖会抛出异常，阻止 Project 创建继续执行。

Phase 2 不执行 DAG，不创建 HumanGate，不生成 RuntimeEvent，不生成 Artifact、Reflection 或 Lessons。这些属于 Phase 3 和后续阶段。

当前本地 demo API 对 `/api/intent/analyze`、`/api/agents`、`/api/agents/recommend`、`/api/projects` 和 `/api/projects/{projectId}` 放开认证。登录、JWT 和 RBAC 后续补齐。

## 测试说明

运行完整后端测试：

```powershell
cd backend-java
mvn test
```

按模块运行：

```powershell
cd backend-java
mvn "-Dtest=IntentAnalysisServiceTest,IntentControllerTest" test
mvn "-Dtest=AgentRecommendationServiceTest,AgentControllerTest,BuiltinAgentSeederTest" test
mvn -Dtest=DagTemplateLoaderTest test
mvn -Dtest=ProjectApplicationServiceTest test
mvn "-Dtest=ProjectControllerTest,IntentControllerTest,AgentControllerTest,SecurityConfigTest" test
```

测试覆盖点包括 deterministic intent、Agent 推荐顺序、skills 数组转换、DAG 模板解析、未知依赖校验、ProjectState 组装、Controller JSON 字段、400 校验错误和 404 not found。

## 排错手册

如果 `DagTemplateLoaderTest` 报模板不存在，确认命令是在 `backend-java` 目录执行，并且 `../templates/dags/credit_card_installment_campaign_v1.yaml` 存在。

如果 Project 创建时 TaskEdge 外键失败，检查 `ProjectApplicationService.saveTasks` 是否在 `saveEdges` 前执行，并确认 task 已经 flush 到数据库。

如果 Controller 测试返回 401，检查 `SecurityConfig` 是否放开了对应 HTTP method 和 path。`GET /api/projects/{projectId}` 需要匹配 `/api/projects/*`。

如果 Agent 推荐为空，先确认 `BuiltinAgentSeeder.seedBuiltinAgents()` 已运行，并检查 `AgentProfileRepository.findAllByEnabledTrueOrderBySortOrderAsc()` 是否返回 PD、DEV、QA、RISK、PMO。

如果 API 返回大写 status 或 kind，检查 `ProjectApplicationService.lower` 和 `IntentController` 的 `Locale.ROOT` 转换。

## 面试讲法

可以把 Phase 2 讲成“从自然语言目标到可持久化任务图”的后端纵切。它展示了 Spring MVC、Service 编排、JPA 事务、模板解析、DTO 映射和 Testcontainers 集成测试。

关键取舍是先 deterministic、后 LLM。这样能保证 demo 稳定，也让系统边界清楚：LLM 只是 intent provider，Project 创建不依赖不可控输出。

另一个重点是状态不只存在前端。Project、Run、Task、Edge 都落库，后续 Runner、SSE 和审计可以基于同一套持久化状态继续推进。

如果被问到为什么 HumanGate 不在 Phase 2 创建，可以回答：HumanGate 是运行到人工确认节点时的运行时状态，应该由 Phase 3 Runner 触发；Phase 2 只负责实例化 DAG，不负责执行 DAG。
