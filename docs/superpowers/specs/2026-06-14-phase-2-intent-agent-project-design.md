# Phase 2 Intent + Agent + Project 创建设计

## 目标

Phase 2 实现严格的后端纵切：信用卡分期研发需求的 Intent 分析、Agent 推荐、`credit_card_installment_campaign_v1` DAG 模板加载、Project / Room / Run / Task / Edge 持久化，以及 `ProjectState` 响应。

本阶段不实现 Runner、HumanGate approve / reject、SSE、审计日志、Artifact、Reflection 或 Lessons 生成。这些能力分别留给 Phase 3 和 Phase 4 之后的阶段。

## 推荐方案

采用 service-centered vertical slice：

- `intent` 模块提供 deterministic intent analyzer。
- `agent` 模块基于已启用 Agent seed data 做推荐。
- `orchestrator/template` 模块加载并校验 YAML DAG 模板。
- `project` 模块提供事务性应用服务，统一创建项目、协同室、运行实例、任务和边。
- Controller 只做请求校验、调用服务和返回 DTO。

相比 controller-first wiring，这种方案测试边界清晰，DAG 校验和持久化行为可以独立覆盖。相比提前引入 Runner/SSE，它保持 Phase 2 边界明确。

## 业务规则

### Intent 分析

默认黄金路径输入为：

```text
请帮我设计一个银行信用卡分期活动配置与审批系统的研发方案，要求支持活动规则配置、审批流、风险校验、灰度发布和操作审计。
```

当目标文本命中银行、信用卡、分期、活动、审批、风险、灰度、审计等关键词时，返回：

- `mode`: `TASKS`
- `template_id`: `credit_card_installment_campaign_v1`
- `domain`: `banking_credit_card`
- `risk_level`: `MEDIUM`
- `human_gate_required`: `true`
- `candidate_roles`: `PD`, `DEV`, `QA`, `RISK`, `PMO`

若文本未命中黄金路径关键词，仍使用 deterministic fallback，返回同一模板和角色集合，但 confidence 降低，便于前端演示和后续真实 LLM 替换。

### Agent 推荐

推荐服务读取 `agents` 表中 `enabled = true` 的 Agent，按 intent 的 `candidate_roles` 和 `sortOrder` 返回。

返回的 Agent summary 包含：

- `id`
- `name`
- `role`
- `skills`
- `score`
- `recommendation_reason`

`skills` 在 DTO 层从逗号分隔字符串转换为数组。

### DAG 模板

模板文件为 `templates/dags/credit_card_installment_campaign_v1.yaml`。

模板加载器负责：

- 读取模板 ID、名称、mode、domain 和 nodes。
- 校验 node ID 唯一。
- 校验所有 `depends_on` 引用存在。
- 保持文件中的 node 顺序作为 `sortOrder`。

Phase 2 不调度 DAG，只实例化 DAG。

### Project 创建

`POST /api/projects` 接收：

```json
{
  "goal": "请帮我设计一个银行信用卡分期活动配置与审批系统的研发方案..."
}
```

事务内创建：

- `Project`: `mode = TASKS`, `status = CREATED`
- `Room`: 名称为 `信用卡分期活动研发协同室`
- `OrchestratorRun`: `templateId = credit_card_installment_campaign_v1`, `status = CREATED`
- `OrchestratorTask`: root task 为 `READY`，有依赖 task 为 `PENDING`
- `TaskEdge`: 每个依赖关系创建一条边

Phase 2 不创建 `HumanGate` 记录，因为 HumanGate 应由 Phase 3 Runner 执行到 `human_gate_prd` 节点时进入等待态。

### ProjectState 响应

`POST /api/projects` 和 `GET /api/projects/{projectId}` 返回同一结构：

- `project`
- `room`
- `agents`
- `run`
- `tasks`
- `human_gate`: `null`
- `artifact`: `null`
- `reflection`: `null`

`tasks[*].depends_on` 从 `TaskEdge` 反查 source node IDs 后组装。

持久化层继续使用现有大写语义值，例如 `TASKS`、`CREATED`、`READY`、`PENDING` 和 `HUMAN_GATE`。API DTO 面向当前 React 客户端返回小写 snake_case，例如 `tasks`、`created`、`ready`、`pending` 和 `human_gate`。

### API

本阶段提供：

- `POST /api/intent/analyze`
- `GET /api/agents`
- `POST /api/agents/recommend`
- `POST /api/projects`
- `GET /api/projects/{projectId}`

为了兼容当前 React demo，本阶段允许这些 API 在本地未登录状态访问。登录、JWT 和 RBAC 在后续阶段补齐。

## 错误处理

- 空 goal 返回 400。
- 未找到项目返回 404。
- 模板文件缺失或模板依赖无效返回 500，并在服务层抛出明确异常。
- 重复 node 或未知依赖会阻止项目创建，避免半成品 DAG 落库。

## 测试策略

遵循 TDD：

1. Intent analyzer 单元测试先覆盖黄金路径和 fallback。
2. Agent recommendation 测试先覆盖角色顺序、skills 数组和推荐原因。
3. Template loader 测试先覆盖模板节点、依赖关系和校验错误。
4. Project application service 集成测试先覆盖 Project / Room / Run / Task / Edge 持久化和 `ProjectState` 组装。
5. Controller 测试先覆盖 JSON 请求、400 校验和正常响应。

验收命令：

```powershell
cd backend-java
mvn test
```

## 非目标

- 不执行 DAG。
- 不生成 Artifact / Reflection / Lessons。
- 不实现 HumanGate approve / reject。
- 不实现 RuntimeEvent 或 SSE。
- 不引入 RabbitMQ、Redis 或真实 LLM。
