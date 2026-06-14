# Phase 05: React Frontend Workbench

## 怎么使用这份文档

这份文档写给“几乎没有前端基础，也不熟悉 TypeScript、React、HTTP API、SSE”的学习者。你不需要先会写完整前端项目。阅读时先把它当成一份带你看项目的讲义，而不是考试资料。

建议按这个顺序读：

1. 先读“业务背景”和“先建立最小前端概念”，知道这次到底在做什么。
2. 再读“核心概念”，把 React、TypeScript、状态、API、SSE 这些词变成普通话。
3. 然后读“端到端流程”，理解从输入目标到展示交付物的完整路径。
4. 最后打开“代码导读”里列出的文件，对着代码一段一段看。
5. 如果测试或页面跑不起来，直接跳到“排错手册”。
6. 如果准备面试，重点看“面试讲法”和“学习检查清单”。

Phase 5 的核心不是“做一个好看的首页”，而是把前几阶段的后端能力做成一个真正可操作的工作台。用户进入页面后，应该可以直接输入目标、分析意图、推荐 Agent、创建项目、启动 run、处理 HumanGate、看事件时间线、看审计记录、看交付物和复盘。

这次前端使用的语言是 TypeScript，也就是带类型检查的 JavaScript。你可以先简单理解为：JavaScript 负责让页面动起来，TypeScript 在写代码时帮你检查“这个变量到底长什么样”。

## 学习目标

学完这一阶段，你应该能回答这些问题：

- 前端、后端、API 分别是什么，它们怎么协作。
- React 组件是什么，为什么要把页面拆成多个组件。
- TypeScript 类型为什么重要，`ProjectState` 这种类型有什么用。
- `useState` 是什么，为什么页面上的输入框、按钮状态、项目状态都要放进 state。
- `useEffect` 是什么，为什么订阅 SSE 要放在 effect 里。
- 什么是受控输入框，为什么 `textarea` 的 value 来自 React state。
- `fetch`、`async`、`await` 是什么，前端如何调用后端接口。
- 为什么 `api.ts` 要单独放 API 调用，而不是写在组件里面。
- 什么是 `EventSource`，它和普通 HTTP 请求有什么区别。
- HumanGate 的 approve 和 reject 在前端怎么触发后端状态变化。
- AuditLog 和 RuntimeEvent 在页面上分别展示什么。
- Artifact、Reflection、Lessons 为什么需要后端最小数据闭环。
- 如何用 Vitest 和 Testing Library 验证一个前端工作流。
- 如何解释 Phase 5 的设计取舍：为什么先做单页工作台，不上路由、登录、WebSocket 和 RabbitMQ worker。

## 业务背景

这个项目模拟的是“信用卡分期活动配置与审批系统研发协作”。用户输入一个目标，例如：

```text
请帮我设计一个银行信用卡分期活动配置与审批系统的研发方案，
要求支持活动规则配置、审批流、风险校验、灰度发布和操作审计。
```

前几阶段已经让后端具备了这些能力：

```text
Phase 2: 根据目标创建 Project、Room、Run、Tasks、DAG edges
Phase 3: Runner 按 DAG 执行任务，并在 HumanGate 停下等待人工确认
Phase 4: 记录 RuntimeEvent、通过 SSE 推送事件、记录 AuditLog
Phase 5: 做一个前端工作台，把这些能力串起来给用户操作和观察
```

Phase 5 之前，后端像一台已经能运行的机器，但是用户还缺少一个控制台。Phase 5 要做的就是控制台：

```text
左侧: 输入目标、查看意图分析、查看推荐 Agent
中间: 创建项目、启动运行、查看 DAG 和任务输出
右侧: 处理 HumanGate、查看事件时间线、查看审计日志
底部: 查看 Artifact、Reflection、Lessons
```

这不是营销页，也不是介绍页。第一屏就是实际工作台。因为这个项目的目标是展示“端到端研发协作闭环”，而不是展示宣传文案。

## 先建立最小前端概念

### 前端是什么

前端就是用户在浏览器里看到和操作的部分。你看到的输入框、按钮、任务卡片、事件列表，都是前端渲染出来的。

前端不直接改数据库。前端做的是：

```text
收集用户输入
调用后端 API
拿到后端返回的数据
把数据渲染成页面
监听后端实时事件
根据用户点击继续调用 API
```

### 后端是什么

后端负责真正的业务逻辑和数据存储。比如：

```text
分析用户目标
推荐 Agent
创建项目和任务
启动 Runner
处理 HumanGate approve/reject
保存 Artifact、Reflection、Lessons
保存 RuntimeEvent 和 AuditLog
```

前端不应该自己判断“这个 run 到底能不能完成”。它应该调用后端，让后端返回权威状态。

### API 是什么

API 可以理解成前端和后端之间约定好的按钮。

例如：

```http
POST /api/intent/analyze
POST /api/agents/recommend
POST /api/projects
POST /api/runs/{runId}/start
POST /api/human-gates/{gateId}/approve
POST /api/human-gates/{gateId}/reject
GET /api/events/stream?run_id=run-xxx
GET /api/audit-logs?actor_id=local-user
```

前端点击按钮后，不是自己完成业务，而是调用这些 API。

### JSON 是什么

JSON 是前后端传数据的常见格式。它长得像这样：

```json
{
  "run": {
    "id": "run-1",
    "status": "waiting_human"
  },
  "human_gate": {
    "id": "gate-1",
    "status": "waiting"
  }
}
```

你可以先把 JSON 理解成“有固定字段名的数据包”。TypeScript 类型就是用来描述这些数据包的形状。

### React 是什么

React 是一个用来写前端页面的库。它的核心思想是：

```text
页面 = 组件 + 状态
```

组件负责“这一块页面长什么样”。状态负责“当前数据是什么”。

例如 `GoalPanel` 组件负责目标输入和两个按钮：

```text
Goal textarea
Analyze button
Create Project button
```

`HumanGatePanel` 组件负责人工确认：

```text
Prompt
Decision reason
Approve button
Reject button
```

### TypeScript 是什么

TypeScript 是 JavaScript 的加强版。它让你在写代码时先定义数据结构。

例如：

```ts
export type RunSummary = {
  id: string;
  project_id: string;
  template_id: string;
  status: string;
};
```

这段的意思是：一个 `RunSummary` 必须有 `id`、`project_id`、`template_id`、`status`，而且它们都是字符串。

如果你把 `id` 写成 number，TypeScript 会在 build 时提醒你。这就是 Phase 5 要把前端 ID 统一改成 string 的原因：后端真实返回的是 UUID 风格字符串，不是数字。

### Vite 是什么

Vite 是前端开发和打包工具。你可以把它理解成：

```text
npm test      跑前端测试
npm run build 检查 TypeScript 并打包生产文件
npm run dev   启动本地前端开发服务器
```

## 核心概念

### 组件

组件就是页面上的一块独立区域。Phase 5 把页面拆成了这些组件：

```text
GoalPanel        目标输入、Analyze、Create Project
IntentPanel      意图分析结果
AgentPanel       推荐 Agent
RunControl       run 状态、模板、进度、Start Run
DagBoard         DAG 任务卡片、选中任务输出
HumanGatePanel   approve/reject 人工确认
EventTimeline    SSE 事件时间线
AuditPanel       审计日志
DeliveryPanel    Artifact、Reflection、Lessons
```

这样拆的好处是：每个文件只关心一块页面，初学者更容易读懂。以后某个区域要改，也不需要在一个巨大文件里找半天。

### Props

Props 是父组件传给子组件的数据和函数。

例如 `App.tsx` 把这些东西传给 `GoalPanel`：

```tsx
<GoalPanel
  goal={goal}
  busy={busyAction !== null}
  onGoalChange={setGoal}
  onAnalyze={handleAnalyze}
  onCreateProject={handleCreateProject}
  canCreateProject={Boolean(intent)}
/>
```

你可以这样理解：

```text
goal                 当前输入框内容
busy                 当前是否有请求正在执行
onGoalChange         输入框变化时调用谁
onAnalyze            点击 Analyze 时调用谁
onCreateProject      点击 Create Project 时调用谁
canCreateProject     创建按钮现在能不能点
```

子组件只负责展示和触发回调，不负责知道整个业务流程。

### State

State 是 React 组件记住的数据。Phase 5 的主状态放在 `App.tsx`：

```ts
const [goal, setGoal] = useState(sampleGoal);
const [intent, setIntent] = useState<IntentAnalysis | null>(null);
const [recommendedAgents, setRecommendedAgents] = useState<AgentSummary[]>([]);
const [projectState, setProjectState] = useState<ProjectState | null>(null);
const [events, setEvents] = useState<EventTimelineItem[]>([]);
const [auditLogs, setAuditLogs] = useState<AuditLogSummary[]>([]);
const [decisionReason, setDecisionReason] = useState("scope confirmed");
```

每个 state 都有两个东西：

```text
当前值
修改当前值的函数
```

例如：

```ts
setProjectState(nextState);
```

这句话的意思是：后端返回了新的项目状态，前端用它刷新页面。

### 受控输入框

`GoalPanel` 和 `HumanGatePanel` 里的 `textarea` 都是受控输入框。

受控的意思是：

```text
输入框显示什么，由 React state 决定
用户打字时，React 更新 state
state 更新后，输入框重新显示新内容
```

典型写法：

```tsx
<textarea
  value={goal}
  onChange={(event) => onGoalChange(event.target.value)}
/>
```

这比让浏览器自己管理输入框更容易测试，也更容易和按钮状态、API 请求联动。

### 条件渲染

条件渲染就是“有数据时显示 A，没有数据时显示 B”。

例如 `IntentPanel`：

```text
有 intent: 显示 domain、risk、confidence、candidate roles
没有 intent: 显示 Pending 状态
```

`HumanGatePanel`：

```text
有 gate: 显示 prompt、reason、Approve、Reject
没有 gate: 显示 No gate
```

这种写法让同一个页面能表达不同业务阶段，而不需要跳转多个页面。

### API Client

`frontend/src/api.ts` 是 API client。它把所有后端请求集中在一个文件里。

例如：

```ts
export async function startRun(runId: string): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/runs/${runId}/start`, {
    method: "POST",
  });
  return parseJsonResponse<ProjectState>(response, "Start run");
}
```

这样做的好处是：

```text
App.tsx 不需要关心 URL 怎么拼
测试可以 mock api.ts 里的函数
以后后端路径变化时，集中改 api.ts
错误处理逻辑可以复用
```

### Promise、async、await

调用后端需要时间，所以 `fetch` 返回的是 Promise。你可以先把 Promise 理解成“未来会有结果的一件事”。

`async` 和 `await` 让代码看起来像同步顺序：

```ts
const nextState = await createProject(goal);
setProjectState(nextState);
```

意思是：

```text
先等后端创建项目完成
拿到返回的 ProjectState
再刷新页面状态
```

### ProjectState

`ProjectState` 是前端最重要的数据结构。它代表当前项目的一整包状态：

```ts
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
```

前端很多地方都从它取数据：

```text
RunControl       读取 project、run、tasks
DagBoard         读取 tasks
HumanGatePanel   读取 human_gate
DeliveryPanel    读取 artifact、reflection、lessons
AgentPanel       可以读取 agents
```

这就是“后端是状态源”的设计。前端不要自己猜 run 是否完成，而是相信后端返回的 `ProjectState`。

### SSE 和 EventSource

普通 API 是一次请求一次响应：

```text
前端 -> 后端: 给我当前项目状态
后端 -> 前端: 这是当前项目状态
连接结束
```

SSE 是后端持续推事件：

```text
前端 -> 后端: 我订阅 run-1 的事件
后端 -> 前端: project.created
后端 -> 前端: run.started
后端 -> 前端: task.completed
连接保持
```

浏览器里用 `EventSource` 订阅：

```ts
const source = new EventSource(eventStreamUrl(projectState.run.id));
```

Phase 5 监听这些事件：

```text
project.created
run.started
task.completed
human_gate.waiting
human_gate.approved
human_gate.rejected
run.completed
run.failed
```

收到事件后，前端把它追加到 `events` state，`EventTimeline` 就会重新渲染。

### AuditLog

RuntimeEvent 回答的是“系统运行到了哪一步”。AuditLog 回答的是“谁做了什么操作”。

前端通过这个接口查询：

```http
GET /api/audit-logs?actor_id=local-user
```

在页面上，AuditPanel 展示：

```text
action
target_type
target_id
payload
```

例如：

```text
HUMAN_GATE_APPROVE
human_gate:gate-1
{"reason":"scope confirmed"}
```

### Artifact、Reflection、Lessons

Artifact 是交付物。Reflection 是复盘。Lessons 是经验沉淀。

Phase 5 做了最小后端数据闭环：当 run 完成时，后端生成并保存：

```text
1 个 Artifact
1 个 Reflection
3 条 Lessons
```

它们不是前端假数据，而是后端持久化后通过 `ProjectStateResponse` 返回给前端。

这一步很重要，因为页面底部的交付区域应该展示真实后端状态，而不是写死的展示文案。

## 设计原因

### 为什么做单页工作台

本阶段所有关键操作都属于同一个工作流：

```text
分析目标 -> 推荐 Agent -> 创建项目 -> 启动 run -> 处理 HumanGate -> 查看结果
```

如果拆成多个页面，初学者会同时面对 React Router、页面跳转、URL 参数、全局状态管理等额外概念。Phase 5 先做单页，是为了把核心业务闭环讲清楚。

后续如果要支持项目列表、历史 run、用户中心，再引入路由更合适。

### 为什么 API 函数独立放在 api.ts

如果所有 `fetch` 都写在 `App.tsx`，App 会同时负责：

```text
页面状态
按钮点击
URL 拼接
HTTP 错误处理
JSON 解析
```

职责太多。把 API 调用放进 `api.ts` 后，App 只需要关心业务动作：

```ts
const nextState = await startRun(projectState.run.id);
```

这句话读起来就是“启动 run 并拿到新状态”，更适合初学者理解。

### 为什么 TypeScript ID 都改成 string

后端的实体 ID 是字符串，例如：

```text
project-xxx
run-xxx
gate-xxx
```

旧前端测试里用过 number，例如 `id: 1`。这会造成契约不一致。Phase 5 把所有前端 ID 改成 string，让前后端类型对齐。

这类问题越早修越好。否则页面可能看起来能跑，但一接真实后端就出现类型错配。

### 为什么 App.tsx 管编排，组件只管展示

`App.tsx` 负责整体流程：

```text
handleAnalyze
handleCreateProject
handleStartRun
handleDecision
refreshAudit
SSE subscription
```

组件负责局部 UI：

```text
GoalPanel 只知道按钮被点了
HumanGatePanel 只知道 Approve 或 Reject 被点了
DagBoard 只知道哪个任务被选中
```

这叫“容器和展示分离”的简单版本。它让测试更清楚，也让每个组件更容易读。

### 为什么 SSE 只更新事件时间线

Phase 5 的源状态仍然来自后端 API 返回的 `ProjectState`。SSE 主要用于事件时间线。

原因是：SSE 事件通常只描述某一步发生了什么，不一定包含完整项目状态。比如：

```json
{
  "event_type": "task.completed",
  "payload": {
    "node_id": "need_analysis"
  }
}
```

这个事件能告诉你 `need_analysis` 完成了，但不能替代完整的 `ProjectState`。因此 Phase 5 采用保守做法：按钮操作后以后端返回的 `ProjectState` 刷新主要界面，SSE 用来展示运行过程。

### 为什么不用 WebSocket

当前页面只需要后端向前端推运行事件。用户的控制动作仍然走普通 HTTP API。

```text
后端 -> 前端: RuntimeEvent
前端 -> 后端: approve/reject/start 仍然走 POST API
```

SSE 更轻，浏览器原生支持，也更容易和 Phase 4 的事件 replay 模型配合。等未来需要多人协作、双向实时编辑、在线 presence，再考虑 WebSocket。

### 为什么本阶段不做登录

Phase 5 是本地学习和演示阶段，重点是前端工作台和后端闭环。登录、JWT、RBAC 会引入大量新概念：

```text
用户表
密码或第三方登录
Token 签发
Token 刷新
权限校验
前端路由守卫
审计 actor 绑定真实用户
```

这些都重要，但不是本阶段核心。当前先用 `local-user` 表示本地操作者。

### 为什么 Artifact/Reflection/Lessons 先用确定性生成

真实系统里，交付物和复盘可能由 LLM 生成。但当前项目先用确定性文本，因为：

```text
测试稳定
不依赖外部模型
初学者能先理解数据流
前端能展示真实持久化数据
```

后续接入 LLM 时，仍然可以复用同一套表结构和前端展示。

## 端到端流程

### 流程一：Analyze

用户点击：

```text
Analyze
```

前端执行：

```ts
const [nextIntent, agents] = await Promise.all([
  analyzeIntent(goal),
  recommendAgents(goal),
]);
```

这会同时调用两个后端接口：

```http
POST /api/intent/analyze
POST /api/agents/recommend
```

返回后，前端设置：

```ts
setIntent(nextIntent);
setRecommendedAgents(agents);
```

页面左侧会出现：

```text
domain
risk level
confidence
candidate roles
recommended agents
```

### 流程二：Create Project

用户点击：

```text
Create Project
```

前端调用：

```http
POST /api/projects
```

后端创建：

```text
Project
Room
Run
Tasks
TaskEdges
RuntimeEvent: project.created
AuditLog: PROJECT_CREATE
```

返回 `ProjectState` 后，前端设置：

```ts
setProjectState(nextState);
setEvents([]);
await refreshAudit();
```

页面中间开始显示 run、template、progress 和 DAG 任务。

### 流程三：订阅 SSE

一旦 `projectState.run.id` 存在，`App.tsx` 的 `useEffect` 会打开 SSE：

```ts
const source = new EventSource(eventStreamUrl(projectState.run.id));
```

然后注册事件监听：

```ts
source.addEventListener("task.completed", handleEvent);
source.addEventListener("human_gate.waiting", handleEvent);
```

收到事件后，前端解析 envelope：

```ts
const envelope = JSON.parse(event.data) as RuntimeEventEnvelope;
```

再追加到时间线：

```ts
setEvents((current) => [...current, item].slice(-30));
```

`slice(-30)` 的意思是最多保留最近 30 条，避免页面无限增长。

### 流程四：Start Run

用户点击：

```text
Start Run
```

前端调用：

```http
POST /api/runs/{runId}/start
```

后端 Runner 开始执行 DAG。它会先完成普通任务，遇到 HumanGate 时暂停。

返回状态通常类似：

```text
run.status = waiting_human
human_gate.status = waiting
need_analysis.status = completed
human_gate_prd.status = waiting_human
```

前端刷新 `projectState` 后，右侧 `HumanGatePanel` 出现 prompt 和 approve/reject 按钮。

### 流程五：Approve

用户填写：

```text
Decision reason = scope confirmed
```

点击：

```text
Approve
```

前端调用：

```http
POST /api/human-gates/{gateId}/approve
```

请求体：

```json
{
  "reason": "scope confirmed",
  "decided_by": "local-user"
}
```

后端会：

```text
标记 gate approved
标记 HumanGate task completed
继续执行后续 DAG
完成 run
生成 Artifact、Reflection、Lessons
写 RuntimeEvent
写 AuditLog
```

前端拿到新的 `ProjectState` 后，底部展示交付物、复盘和经验。

### 流程六：Reject

如果用户点击：

```text
Reject
```

前端调用：

```http
POST /api/human-gates/{gateId}/reject
```

后端会：

```text
标记 gate rejected
标记 run failed
标记 project failed
记录 human_gate.rejected
记录 run.failed
记录 HUMAN_GATE_REJECT
```

这条路径验证的是“人工关口可以阻止错误流程继续扩大”。

## 代码导读

### types.ts

文件：

```text
frontend/src/types.ts
```

这是前端和后端 API 契约的 TypeScript 版本。

重点看这些类型：

```text
IntentAnalysis
AgentSummary
ProjectState
RuntimeEventEnvelope
EventTimelineItem
AuditLogSummary
HumanGateDecision
```

阅读建议：

1. 先看 `ProjectState`，因为它是主状态。
2. 再看 `TaskSummary`，理解 DAG 卡片需要什么字段。
3. 再看 `HumanGateSummary`，理解人工审批区域需要什么字段。
4. 最后看 `RuntimeEventEnvelope` 和 `AuditLogSummary`，理解右侧面板的数据来源。

### api.ts

文件：

```text
frontend/src/api.ts
```

这个文件封装所有后端调用：

```text
analyzeIntent(goal)
recommendAgents(goal)
createProject(goal)
startRun(runId)
decideHumanGate(gateId, decision, reason, decidedBy)
listAuditLogs(actorId)
eventStreamUrl(runId)
```

重点看 `parseJsonResponse`：

```ts
if (!response.ok) {
  throw new Error(`${action} failed: ${response.status}`);
}
```

这表示只要后端返回非 2xx，前端就抛出错误，App 会把错误放进 `error` state 并显示。

### App.tsx

文件：

```text
frontend/src/App.tsx
```

这是 Phase 5 前端的编排中心。

它主要做五件事：

```text
保存页面状态
处理按钮点击
调用 api.ts
订阅 SSE
把 state 分发给各个组件
```

建议阅读顺序：

1. 先看顶部的 state 定义。
2. 再看 `handleAnalyze`。
3. 再看 `handleCreateProject`。
4. 再看 `handleStartRun`。
5. 再看 `handleDecision`。
6. 最后看 JSX 布局，理解左右中三栏怎么组装。

### GoalPanel.tsx

文件：

```text
frontend/src/components/GoalPanel.tsx
```

负责：

```text
目标输入框
Analyze 按钮
Create Project 按钮
```

它不直接调用后端。它只调用父组件传入的函数：

```text
onAnalyze
onCreateProject
onGoalChange
```

### IntentPanel.tsx

文件：

```text
frontend/src/components/IntentPanel.tsx
```

负责展示意图分析结果：

```text
template_id
domain
risk_level
confidence
candidate_roles
```

如果还没有分析结果，它显示 Pending。

### AgentPanel.tsx

文件：

```text
frontend/src/components/AgentPanel.tsx
```

负责展示推荐 Agent：

```text
name
role
skills
score
recommendation_reason
```

注意 `score` 是后端推荐分数，前端只展示，不重新计算。

### RunControl.tsx

文件：

```text
frontend/src/components/RunControl.tsx
```

负责展示 run 的摘要：

```text
project status
run status
template id
completed tasks / total tasks
```

它也提供 `Start Run` 按钮。

### DagBoard.tsx

文件：

```text
frontend/src/components/DagBoard.tsx
```

负责展示任务卡片。每个 task tile 包含：

```text
role
name
status
```

点击某个任务后，下方会展示该任务的 output 或 log。

### HumanGatePanel.tsx

文件：

```text
frontend/src/components/HumanGatePanel.tsx
```

负责人工确认：

```text
prompt
Decision reason
Approve
Reject
```

`Decision reason` 是测试里明确验证的 label，因为这保证输入框可访问，也方便自动化测试定位。

### EventTimeline.tsx

文件：

```text
frontend/src/components/EventTimeline.tsx
```

负责展示 SSE 事件：

```text
event_type
payload
```

它不负责打开 SSE 连接。打开连接的逻辑在 `App.tsx`。

### AuditPanel.tsx

文件：

```text
frontend/src/components/AuditPanel.tsx
```

负责展示审计日志：

```text
action
target_type
target_id
payload
```

它展示的是 `listAuditLogs("local-user")` 的结果。

### DeliveryPanel.tsx

文件：

```text
frontend/src/components/DeliveryPanel.tsx
```

负责底部交付区域：

```text
Artifact
Reflection
Lessons
```

这些数据来自后端 `ProjectStateResponse`。

### App.css

文件：

```text
frontend/src/App.css
```

Phase 5 的布局重点是三栏工作台：

```css
.workspace-shell {
  display: grid;
  grid-template-columns: minmax(250px, 300px) minmax(0, 1fr) minmax(280px, 340px);
}
```

移动端会堆叠：

```css
@media (max-width: 980px) {
  .workspace-shell {
    grid-template-columns: 1fr;
  }
}
```

任务卡片设置了固定最小高度和换行，避免状态变化导致布局跳动：

```css
.task-tile {
  min-height: 136px;
  overflow-wrap: anywhere;
}
```

### 后端 DeliveryGenerationService

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/growth/service/DeliveryGenerationService.java
```

它在 run 完成时生成：

```text
Artifact
Reflection
Lessons
```

这个服务的关键点是幂等：

```text
如果 artifact 已存在，不重复创建
如果 reflection 已存在，不重复创建
如果 lessons 已存在，不重复创建
```

这样重复读取或重复触发已完成 run 时，不会产生一堆重复数据。

### ProjectStateResponse

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectStateResponse.java
```

Phase 5 给它增加了：

```text
List<LessonSummary> lessons
```

并让 `ProjectApplicationService` 从数据库读取 artifact、reflection、lessons 后返回给前端。

## 边界条件

### Analyze 还没完成时不能创建项目

`Create Project` 按钮依赖：

```ts
canCreateProject={Boolean(intent)}
```

这表示先分析目标，再创建项目。这样页面流程更清楚。

### 没有 projectState 时不能启动 run

`Start Run` 需要 `projectState.run.id`。如果还没有项目，就没有 run ID。

### run 不是 created 时不允许重复 start

`RunControl` 里：

```ts
const canStart = Boolean(state?.run.id) && state?.run.status === "created";
```

这样避免用户在 waiting、completed、failed 状态下继续点击 start。

### 没有 HumanGate 时不能 approve/reject

`handleDecision` 里先取：

```ts
const gate = projectState?.human_gate;
if (!gate) {
  return;
}
```

没有 gate 就不调用后端。

### gate 不在 waiting 时按钮禁用

`HumanGatePanel` 里：

```ts
const waiting = gate?.status === "waiting";
```

只有 waiting 状态允许 approve/reject。

### SSE 重复事件不重复展示

SSE 可能因为重连或 replay 收到重复事件。前端用 event id 去重：

```ts
if (current.some((item) => item.id === envelope.id)) {
  return current;
}
```

### SSE 事件只保留最近 30 条

```ts
return [...current, item].slice(-30);
```

这样长时间运行时页面不会无限增长。

### API 失败会显示错误

所有主要动作都经过 `runAction`：

```ts
try {
  await callback();
} catch (err) {
  setError(err instanceof Error ? err.message : "Request failed");
}
```

这样用户至少能看到请求失败，而不是按钮点了没有反应。

### 文本必须允许换行

项目里有长模板 ID、JSON payload、中文长句。CSS 使用：

```css
overflow-wrap: anywhere;
white-space: pre-wrap;
```

这是为了避免文字挤出卡片。

## 测试说明

### 前端测试怎么跑

在前端目录执行：

```powershell
cd frontend
npm test
```

生产 build：

```powershell
npm run build
```

`npm test` 使用 Vitest 和 Testing Library。Testing Library 的思想是尽量像用户一样找页面元素，例如：

```ts
screen.getByRole("button", { name: /Analyze/i })
screen.getByLabelText("Decision reason")
```

这比用 class 名查元素更可靠，因为它同时检查了可访问性。

### App.test.tsx 覆盖什么

文件：

```text
frontend/src/App.test.tsx
```

它覆盖三条主路径：

```text
golden path: Analyze -> Create Project -> Start Run -> Approve -> Delivery
SSE path: 创建项目后订阅 EventSource，并展示 task.completed
reject path: 点击 Reject 后调用 decideHumanGate(gateId, "reject", reason, "local-user")
```

### 为什么测试 mock api.ts

测试不是为了验证浏览器真的连上后端，而是验证前端行为。

所以测试 mock 这些函数：

```ts
vi.spyOn(api, "analyzeIntent").mockResolvedValue(intent);
vi.spyOn(api, "recommendAgents").mockResolvedValue(agents);
vi.spyOn(api, "createProject").mockResolvedValue(waitingProjectState);
```

这样测试稳定，不依赖后端服务是否正在运行。

### 为什么测试 fake EventSource

真实 `EventSource` 需要浏览器和后端连接。测试里不启动后端，所以定义了 `FakeEventSource`。

测试可以手动发事件：

```ts
FakeEventSource.instances[0].emit(
  "task.completed",
  JSON.stringify({
    id: "event-1",
    run_id: "run-1",
    event_type: "task.completed",
    payload: { node_id: "need_analysis" },
    created_at: "2026-06-14T00:00:00Z"
  })
);
```

这能验证前端收到 SSE envelope 后会把它渲染到时间线。

### 后端 delivery 测试怎么跑

在后端目录执行：

```powershell
cd backend-java
mvn "-Dtest=RunnerDeliveryIntegrationTest" test
```

这个测试验证：

```text
approve 后 run completed
ProjectState 返回 artifact
ProjectState 返回 reflection
ProjectState 返回 lessons
数据库里确实保存了 artifact/reflection/lessons
重复触发 completed run 不重复生成数据
```

### 完整验证

最终应执行：

```powershell
cd backend-java
mvn test
```

```powershell
cd frontend
npm test
npm run build
```

如果这三类验证都通过，说明后端、前端测试和生产打包都没有明显断裂。

## 排错手册

### 问题一：npm run build 报 ID 类型错误

如果看到：

```text
Type 'number' is not assignable to type 'string'
```

说明某个 fixture 或组件还在用旧的数字 ID。检查：

```text
frontend/src/types.ts
frontend/src/App.test.tsx
```

Phase 5 里所有业务 ID 都应该是 string。

### 问题二：找不到 confirmHumanGate

如果看到：

```text
Module "./api" has no exported member confirmHumanGate
```

说明旧 App 还没有迁移。Phase 5 不再使用 `/confirm`，而是：

```text
decideHumanGate(gateId, "approve", reason, "local-user")
decideHumanGate(gateId, "reject", reason, "local-user")
```

后端接口是：

```http
POST /api/human-gates/{gateId}/approve
POST /api/human-gates/{gateId}/reject
```

### 问题三：测试找不到按钮

如果 Testing Library 报：

```text
Unable to find an accessible element with the role "button" and name /Analyze/i
```

先检查页面按钮文本是否真的叫：

```text
Analyze
Create Project
Start Run
Approve
Reject
```

测试按可访问名称找按钮。图标要设置 `aria-hidden`，否则可能干扰按钮名称。

### 问题四：测试找不到 Decision reason

检查 `HumanGatePanel` 里 label 是否写成：

```tsx
<span>Decision reason</span>
<textarea ... />
```

或者更标准地使用 `htmlFor` 和 `id`。当前写法通过 label 包裹 textarea，也能让测试通过。

### 问题五：SSE 时间线没有事件

按顺序检查：

1. `projectState.run.id` 是否存在。
2. `eventStreamUrl(runId)` 拼出来的 URL 是否正确。
3. `runtimeEventTypes` 是否包含后端实际事件名。
4. 后端 SSE data 是否是 `RuntimeEventEnvelope` JSON。
5. `JSON.parse(event.data)` 是否成功。
6. `EventTimeline` 是否收到 `events` props。

最常见问题是事件名不一致。例如后端发 `task.completed`，前端却监听 `run.task.completed`。

### 问题六：Artifact 不显示

先确认 approve 后后端返回的 `ProjectState` 里有：

```json
{
  "artifact": "...",
  "reflection": "...",
  "lessons": []
}
```

如果后端没有返回，检查：

```text
DeliveryGenerationService.generateIfMissing(...)
RunnerService 在 run completed 前是否调用它
ProjectApplicationService 是否读取 artifact/reflection/lessons
```

如果后端返回了但页面不显示，检查：

```text
DeliveryPanel props
projectState?.artifact
projectState?.reflection
projectState?.lessons
```

### 问题七：AuditPanel 一直为空

检查前端是否调用：

```ts
listAuditLogs("local-user")
```

再检查后端是否已有对应 actor 的 audit log。

当前 demo 使用固定 actor：

```text
local-user
```

如果后端写入的 actor 不是 `local-user`，前端查询就看不到。

### 问题八：中文在 PowerShell 里看起来乱码

这不一定是文件坏了。PowerShell 默认解码或终端编码可能导致显示乱码。

如果要确认文件是否真是 UTF-8，可以用显式 UTF-8 读取，或者直接让 Java/TypeScript 编译器和测试来验证。不要只根据终端显示就改源码里的中文。

### 问题九：页面文字挤出卡片

检查 CSS 是否保留：

```css
overflow-wrap: anywhere;
white-space: pre-wrap;
```

长模板 ID、JSON payload、中文长句都需要换行能力。

### 问题十：按钮点击后一直禁用

检查 `busyAction` 是否在 finally 里恢复：

```ts
finally {
  setBusyAction(null);
}
```

如果请求抛错但没有 finally，按钮可能一直处于 busy 状态。

## 面试讲法

可以这样介绍 Phase 5：

> Phase 5 我实现的是前端工作台和最小交付数据闭环。后端已经具备创建项目、执行 DAG、HumanGate、SSE 和审计能力，Phase 5 用 React + TypeScript 做了一个单页 operational workbench。左侧负责目标输入、意图分析和 Agent 推荐，中间负责 run 控制、DAG 展示和任务输出，右侧负责 HumanGate approve/reject、事件时间线和 audit log，底部展示 Artifact、Reflection 和 Lessons。

如果面试官问为什么用 TypeScript：

> 因为前后端之间有明确 JSON 契约，例如 ProjectState、TaskSummary、RuntimeEventEnvelope、AuditLogSummary。TypeScript 可以在 build 阶段发现 ID 类型不一致、字段缺失、旧 API 没迁移等问题。Phase 5 里就把所有业务 ID 统一成 string，避免前端用 number fixture 和后端 UUID 字符串不一致。

如果面试官问 React 状态怎么设计：

> 我把全局工作流状态集中在 App.tsx：goal、intent、recommendedAgents、projectState、events、auditLogs、decisionReason、busyAction 和 error。ProjectState 是后端返回的权威状态，按钮操作后用新的 ProjectState 刷新主界面。SSE 只补充事件时间线，不替代 ProjectState。

如果面试官问为什么把 API 封装到 api.ts：

> 这样 App 不直接拼 URL，也不关心 HTTP 错误处理细节。组件和 App 调用的是语义化函数，比如 startRun、decideHumanGate、listAuditLogs。测试时可以直接 mock api.ts，验证前端行为而不依赖后端服务。

如果面试官问为什么用 SSE 而不是 WebSocket：

> 当前需求是服务端向前端单向推送运行事件，用户控制动作仍然走普通 POST API。SSE 基于 HTTP，浏览器原生 EventSource 支持，和 Phase 4 的持久化事件 replay 模型匹配。等需要双向实时协作时再引入 WebSocket。

如果面试官问 Artifact/Reflection/Lessons 为什么不是前端假数据：

> 因为交付物属于业务结果，不应该由前端伪造。Phase 5 在后端增加 DeliveryGenerationService，在 run completed 时持久化 artifact、reflection 和 lessons，再通过 ProjectStateResponse 返回。这样页面展示的是后端真实状态，也能被测试验证。

如果面试官问测试策略：

> 前端用 Vitest 和 Testing Library 覆盖 golden path、SSE envelope 渲染和 reject 决策。测试 mock api.ts，fake EventSource，关注用户能看到和能点击的行为。后端用 RunnerDeliveryIntegrationTest 验证 approve 后生成并返回交付数据，并验证幂等性。

## 学习检查清单

读完后，用这些问题检查自己是否真的理解：

- 我能不能说清楚前端、后端、API 的分工。
- 我能不能说出为什么 Phase 5 是工作台而不是 landing page。
- 我能不能解释 React 组件、props、state 的区别。
- 我能不能说清楚 `useState` 在 App.tsx 里保存了哪些业务状态。
- 我能不能解释 `useEffect` 为什么适合打开和关闭 EventSource。
- 我能不能说出 `ProjectState` 里每个大字段的作用。
- 我能不能解释为什么所有 ID 都用 string。
- 我能不能找到 `Analyze` 点击后调用的两个 API。
- 我能不能找到 `Create Project`、`Start Run`、`Approve`、`Reject` 分别调用哪个函数。
- 我能不能解释 `Decision reason` 是怎么从输入框传到后端的。
- 我能不能说明 SSE envelope 里 `event_type` 和 `payload` 的作用。
- 我能不能说明 RuntimeEvent 和 AuditLog 的区别。
- 我能不能解释 Artifact、Reflection、Lessons 为什么要由后端生成。
- 我能不能运行 `npm test` 并看懂失败信息。
- 我能不能运行 `npm run build` 并理解 TypeScript 错误。
- 我能不能运行后端 delivery 集成测试。
- 我能不能用面试语言讲清楚 Phase 5 的设计取舍。
