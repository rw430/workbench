# Java AgentOps 信用卡分期研发协同工作台设计

## 1. 目标

本项目的最终目标是构建一个 Java 后端为主的 AI AgentOps 多智能体研发协同工作台。它要能作为完整学习项目和面试展示项目使用，而不是只做一个静态 demo。

第一版黄金场景固定为：

```text
银行信用卡分期活动配置与审批系统研发协同
```

工作台本身不是信用卡分期生产系统，而是一个研发协同平台。用户输入这类银行金融科技需求后，系统完成：

```text
意图识别
-> Agent 推荐
-> Project / Room / Run / DAG 创建
-> Runner 执行
-> HumanGate 人工确认
-> 风险合规评审
-> 技术方案与测试方案
-> 交付物
-> REFLECTION
-> Lessons
```

项目目标同时包含三类价值：

- 演示价值：能通过前端完整演示从输入到交付复盘的闭环。
- 后端价值：体现 Java、Spring Boot、数据库、异步、事件、权限、审计、部署等工程能力。
- 学习价值：每个模块都有足够深入的学习文档，帮助理解为什么这么做、边界在哪里、面试怎么讲。

## 2. 当前代码复用

当前可复用的 Java 工作在根目录 `backend-java/`，已经从原 worktree 迁移为主工程。

可直接复用：

- Java 21 + Spring Boot 3 + Maven 工程。
- PostgreSQL + Flyway migration。
- JPA Entity 和 Repository。
- Testcontainers PostgreSQL 集成测试。
- Auth、Agent、Project、OMA、Growth、Audit 数据底座。
- Health endpoint、Actuator、基础 Spring Security 配置。

已完成的核心数据模型包括：

- `Project`
- `Room`
- `AgentProfile`
- `OrchestratorRun`
- `OrchestratorTask`
- `TaskEdge`
- `HumanGate`
- `RuntimeEvent`
- `Artifact`
- `Reflection`
- `Lesson`
- `EvolutionRecord`
- `AuditLog`

需要改造：

- Agent seed data 使用“信用卡分期活动研发协同”语义。
- DAG 模板使用 `credit_card_installment_campaign_v1`。
- 前端默认 API 指向 Java 服务 `http://127.0.0.1:8889`。
- 旧 Python 方案不再作为正式目标后端。

已删除或归档：

- 旧 Python backend。
- 临时 worktree 和 brainstorm 产物。
- 旧 Python 方案设计/计划。
- 原始中文需求资料移动到 `docs/archive/`。

## 3. 目标目录结构

```text
backend-java/
  src/main/java/com/xiaoc/workbench/
    auth/
    agent/
    project/
    intent/
    orchestrator/
    event/
    growth/
    governance/
    common/
frontend/
templates/
  dags/
infra/
scripts/
docs/
  product/
  architecture/
  learning/
  adr/
  api/
  runbooks/
  archive/
```

## 4. 产品边界

### 4.1 第一版必须实现

- 登录和当前用户。
- Intent 分析。
- Agent 推荐。
- 信用卡分期活动研发协同 DAG 模板加载。
- Project / Room / Run / Task / Edge 初始化。
- 异步 Runner 推进 DAG。
- HumanGate approve / reject。
- RuntimeEvent 落库。
- SSE 事件流和 replay。
- Artifact、Reflection、Lessons 生成。
- React 前端展示完整工作台。
- 审计日志记录关键操作。
- Docker Compose 本地完整启动。
- 每个阶段配套学习文档。

### 4.2 第一版不做

- 不接银行真实系统。
- 不使用真实客户、交易、风控策略或内部系统数据。
- 不做真实信用审批或额度决策。
- 不做完整 Agent 市场商业化能力。
- 不做 Kubernetes 生产部署。
- 不强依赖真实 LLM，先提供 deterministic fallback。

## 5. 黄金路径

示例输入：

```text
请帮我设计一个银行信用卡分期活动配置与审批系统的研发方案，
要求支持活动规则配置、审批流、风险校验、灰度发布和操作审计。
```

系统识别结果：

- `mode`: `TASKS`
- `template_id`: `credit_card_installment_campaign_v1`
- `domain`: `banking_credit_card`
- `risk_level`: `MEDIUM`
- `human_gate_required`: `true`

推荐 Agent：

- `PD`：需求分析和 PRD 生成。
- `DEV`：技术方案、接口、状态机和中间件设计。
- `QA`：测试计划、验收标准、回归范围。
- `PMO`：交付汇总、推进记录、复盘。
- `RISK`：风险与合规边界评审。

DAG：

```text
need_analysis
-> human_gate_prd
-> risk_compliance_review
-> tech_design
-> test_plan
-> delivery_summary
-> reflection
-> lessons_extract
```

HumanGate 放在 PRD 草稿之后。原因是银行业务需求在进入技术方案前必须确认范围、合规边界和不触碰敏感数据。

## 6. 后端架构

### 6.1 分层

```text
Controller
-> Application Service
-> Domain Service
-> Repository
-> Database / Redis / MQ / LLM Provider
```

Controller 负责：

- 参数校验。
- 当前用户解析。
- 调用 Application Service。
- 返回 DTO。

Application Service 负责：

- 事务边界。
- 跨领域对象编排。
- 权限检查。
- 审计记录。
- 事件发布。

Domain Service 负责：

- Intent 路由规则。
- Agent 推荐评分。
- DAG 校验。
- Runner 状态机。
- Executor 输出生成。

Repository 负责：

- JPA 查询。
- 持久化状态。
- 组合查询。

### 6.2 领域模块

`auth`

- `POST /api/auth/login`
- `GET /api/auth/me`
- JWT 认证。
- RBAC：ADMIN、PD、DEV、QA、PMO、RISK、VIEWER。

`intent`

- `POST /api/intent/analyze`
- 识别银行信用卡分期活动研发场景。
- 输出 mode、template、confidence、risk_level、candidate_agents。

`agent`

- `GET /api/agents`
- `POST /api/agents/recommend`
- 内置 Agent 初始化。
- 后续支持启用、禁用、克隆。

`project`

- `POST /api/projects`
- `GET /api/projects`
- `GET /api/projects/{projectId}`
- 创建 Project、Room、Run、Task、Edge。

`orchestrator`

- DAG 模板加载。
- DAG 校验。
- Runner 入队。
- Executor 调度。
- HumanGate pause / approve / reject。

`event`

- RuntimeEvent 落库。
- `GET /api/events/stream?run_id=...`
- 支持 last event replay。

`growth`

- Reflection 查询。
- Lessons 查询。
- Lessons 生成。

`governance`

- AuditLog 写入。
- 审计查询。
- 记录登录、项目创建、审批、拒绝、失败、重试、经验抽取。

`common`

- 统一错误响应。
- ID 生成。
- 时间抽象。
- Trace ID。
- 配置属性。

## 7. 数据设计

当前表可以继续使用。

需要新增或调整：

- `agents` seed data 增加 `RISK`。
- `runtime_events.payload` 后续可以从 `text` 升级为 `jsonb`。
- `orchestrator_tasks.kind` 和 `status` 在 Service 层收敛为 enum。
- 如需支持多 Artifact，可将 `artifacts.run_id unique` 改为 `(run_id, artifact_type)`。
- 如需支持用户归属，`projects` 后续增加 `owner_id`。

第一版不急于重构已有表，优先在 Service 和 DTO 层建立稳定语义。

## 8. 异步与中间件

### 8.1 PostgreSQL

承担强一致状态：

- Project。
- Run。
- Task。
- HumanGate。
- RuntimeEvent。
- AuditLog。
- Reflection。
- Lessons。

### 8.2 Redis

第一版用途：

- project create 幂等 token。
- run trigger 分布式锁。
- 高频只读数据缓存。
- 简单限流。

### 8.3 RabbitMQ

第一版用途：

- Runner 异步任务队列。
- `run.created` 后投递 `run_id`。
- Runner 消费后推进 DAG。

选择 RabbitMQ 的原因：

- 面试叙事清楚。
- 适合任务队列。
- 本地 Docker Compose 容易启动。
- 比 Kafka 更轻，不扩大项目范围。

### 8.4 SSE

事件必须先落库，再推 SSE。

原因：

- 前端断线后可以 replay。
- 运行历史可审计。
- Runner 和前端连接解耦。

## 9. Runner 状态机

Run 状态：

- `CREATED`
- `RUNNING`
- `WAITING_HUMAN`
- `COMPLETED`
- `FAILED`
- `CANCELED`

Task 状态：

- `PENDING`
- `READY`
- `RUNNING`
- `WAITING_HUMAN`
- `COMPLETED`
- `FAILED`
- `SKIPPED`

执行规则：

1. Project 创建时实例化 DAG。
2. 所有无依赖任务进入 `READY`。
3. Runner 取 `READY` task 执行。
4. 执行前写 `run.task.running`。
5. 执行成功写 output 和 `run.task.completed`。
6. 遇到 HumanGate 时 task/run/project 进入等待态。
7. approve 后恢复 Runner。
8. reject 后 task/run/project 进入 failed，并写审计。
9. 全部 task completed 后生成 Artifact、Reflection、Lessons。

幂等规则：

- 已完成 task 不重复执行。
- 已审批 HumanGate 不重复改变决策。
- 同一 run 同一 node 只能有一个 task。
- Runner 获取 run 锁后才能推进。

## 10. 前端设计

第一版页面：

- 首页智能路由：输入目标，展示意图和推荐 Agent。
- 项目列表：查看已有项目。
- 项目详情：DAG、当前状态、任务输出。
- HumanGate 面板：approve / reject。
- 事件流 Timeline：SSE 实时事件。
- 交付物预览：PRD、技术方案、测试方案、交付汇总。
- Reflection / Lessons：复盘与经验。
- 审计日志：关键操作记录。

前端原则：

- 前端不实现调度规则。
- 前端只消费后端 ProjectState、TaskState、EventEnvelope。
- SSE 事件用于增量展示，最终状态仍以 HTTP 查询为准。

## 11. 学习文档体系

不沿用旧 Phase 1 学习文档模板。

新的学习文档参考：

- Diataxis：教程、How-to、解释、参考分离。
- Google developer docs：任务导向、明确结果。
- Microsoft Learn：学习目标、前置条件、练习、知识检查。
- ADR：记录技术决策和取舍。

每个模块学习文档必须包含：

1. 学习目标。
2. 业务背景。
3. 核心概念。
4. 设计原因。
5. 端到端流程。
6. 代码导读。
7. 边界条件。
8. 测试说明。
9. 排错手册。
10. 面试讲法。

文档目录：

```text
docs/learning/
docs/adr/
docs/product/
docs/architecture/
docs/api/
docs/runbooks/
```

## 12. 测试策略

后端：

- Controller test：API 参数、状态码、错误响应。
- Service test：Intent、Agent 推荐、DAG 实例化、状态机。
- Repository integration test：PostgreSQL、Flyway、JPA 映射。
- Runner integration test：任务推进、HumanGate、失败重试。
- SSE test：事件落库和 replay。
- Security test：JWT、RBAC、未授权访问。

前端：

- 首页意图分析。
- 创建项目。
- DAG 渲染。
- HumanGate 审批。
- EventTimeline。
- Reflection/Lessons。

端到端：

1. 登录。
2. 输入信用卡分期活动研发需求。
3. 创建项目。
4. 查看 Agent Team。
5. 查看 DAG 停在 HumanGate。
6. approve。
7. 等待 run completed。
8. 查看 Artifact、Reflection、Lessons、AuditLog。

## 13. 分阶段实施

### Phase 1：已完成并整理

- Java 工程。
- Flyway。
- JPA。
- 基础领域模型。
- Repository 测试。
- 项目目录重组。

### Phase 2：Intent + Agent + Project 创建

- 信用卡分期场景 Intent。
- `credit_card_installment_campaign_v1` 模板加载。
- Agent 推荐。
- 创建 Project / Room / Run / Task / Edge。
- 返回 ProjectState。
- 学习文档：`docs/learning/phase-02-intent-agent-project.md`。

### Phase 3：Runner + HumanGate

- RabbitMQ runner queue。
- Runner 状态机。
- deterministic executor。
- HumanGate approve / reject。
- 幂等与失败处理。
- 学习文档：`docs/learning/phase-03-runner-human-gate.md`。

### Phase 4：Event + SSE + Audit

- RuntimeEvent JSON envelope。
- SSE replay。
- AuditLog 关键操作。
- 学习文档：`docs/learning/phase-04-sse-event-audit.md`。

### Phase 5：React 工作台

- 多页面布局。
- 首页智能路由。
- 项目详情和 DAG。
- HumanGate 面板。
- EventTimeline。
- Artifact / Reflection / Lessons。
- 学习文档：`docs/learning/phase-05-frontend-workbench.md`。

### Phase 6：中间件与部署

- Redis。
- RabbitMQ。
- Docker Compose。
- 配置和启动顺序。
- Runbook。
- 学习文档：`docs/learning/phase-06-redis-mq-docker-compose.md`。

### Phase 7：面试演示与复盘

- 演示脚本。
- 架构图。
- 常见追问。
- 故障排查。
- 学习文档：`docs/learning/phase-07-interview-demo-guide.md`。

## 14. 验收标准

完成后必须满足：

- 根目录结构清晰，没有旧 Python 主线和临时 worktree。
- `backend-java` 可以 `mvn test`。
- `frontend` 可以 `npm test` 和 `npm run build`。
- Docker Compose 可以启动完整系统。
- 前端能完成黄金路径演示。
- Java 后端负责主业务闭环。
- 关键状态全部落库。
- SSE 可断线 replay。
- HumanGate 支持 approve/reject。
- AuditLog 可查询关键操作。
- Reflection 和 Lessons 可展示。
- 每个阶段都有学习文档。

## 15. 风险

范围过大：

- 用黄金路径纵切推进，避免一次做全平台。

学习文档空泛：

- 每篇必须有代码导读、边界条件、排错和面试追问。

真实 LLM 不稳定：

- deterministic fallback 为默认，真实 LLM 后续可配置。

中间件拖慢进度：

- 先完成 Java 主闭环，再引入 Redis 和 RabbitMQ；设计上预留接口。

银行业务敏感：

- 使用抽象场景，不使用真实客户、真实系统名、真实风控策略。
