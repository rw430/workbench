# Phase 04: SSE Event + Audit Log

## 怎么使用这份文档

这份文档写给“基本没做过后端项目”的学习者。你不需要先懂 Spring Boot、SSE、事务、审计日志或消息系统。我会先用普通话解释概念，再把概念放回本项目的代码里。

建议按这个顺序阅读：

1. 先读“业务背景”和“先建立最小后端概念”，把问题理解成真实业务里的“我要看见系统发生了什么”。
2. 再读“核心概念”，理解 event、SSE、replay、audit log 分别是什么。
3. 然后读“端到端流程”，把一次创建项目、启动 run、人工确认的过程串起来。
4. 最后打开“代码导读”里的文件，对着代码看每一层为什么存在。
5. 如果测试失败，直接跳到“排错手册”。
6. 如果准备面试，重点读“面试讲法”。

Phase 3 完成后，系统已经可以创建项目、启动 run、执行 DAG、停在 HumanGate、再由人 approve 或 reject。Phase 4 解决的是另一个问题：系统运行时发生了什么，前端怎么实时看到，事后怎么查证是谁做了关键操作。

## 学习目标

学完这一阶段，你应该能回答这些问题：

- 什么是 runtime event，为什么它不是普通日志。
- 什么是 SSE，为什么这一阶段不用 WebSocket。
- 什么是 replay，为什么浏览器断线重连后不能丢事件。
- 为什么事件要先存数据库，再推给前端。
- audit log 是什么，和 runtime event 有什么区别。
- `RuntimeEventService`、`RuntimeEventStreamService`、`EventController` 各自负责什么。
- `AuditLogService` 和 `AuditLogController` 怎么记录和查询审计日志。
- Runner 在哪些关键节点写 runtime event 和 audit log。
- 为什么本阶段仍然保留本地 demo 访问，不引入真正登录、RBAC 和 RabbitMQ。
- 如何用测试验证事件、SSE 入口和审计查询。

## 业务背景

这个项目模拟的是“信用卡分期活动配置与审批系统研发协同”。用户输入一个目标后，系统会把目标拆成一条工作流：

```text
需求分析
-> PRD 范围确认
-> 风险与合规评审
-> 技术方案设计
-> 测试与验收方案
-> 交付汇总
-> 任务复盘
-> 经验沉淀
```

Phase 3 让这条工作流真的能往前跑。比如：

1. 用户创建项目。
2. 系统创建 run 和 tasks。
3. 用户启动 run。
4. Runner 执行第一个自动任务。
5. Runner 遇到 `HUMAN_GATE`，停下来等人确认。
6. 人 approve 后，Runner 继续执行后面的任务。
7. 所有任务完成后，run 变成 completed。

但是，如果只有状态表，使用体验还不够好。前端想展示“时间线”，需要知道每一步发生了什么；用户刷新页面或网络断开后，也希望能补回错过的事件；管理员想追责时，需要知道是谁 approve 了某个 gate、是谁启动了某个 run。

所以 Phase 4 加了两类记录：

```text
RuntimeEvent: 给运行时页面和时间线看的事件
AuditLog: 给追责、审计、排查操作来源看的记录
```

它们看起来都像“日志”，但用途不同。RuntimeEvent 回答“系统发生了什么”；AuditLog 回答“谁对什么对象做了什么操作”。

## 先建立最小后端概念

### 什么是 HTTP API

HTTP API 可以理解成后端暴露给前端的“按钮”。前端访问一个地址，后端就做一件明确的事。

Phase 4 新增了两个读接口：

```http
GET /api/events/stream?run_id=run-xxx
GET /api/audit-logs?actor_id=local-user
```

第一个接口不是普通 JSON 接口，而是 SSE 流。它会保持连接，持续把运行时事件推给浏览器。

第二个接口是普通 JSON 查询。它用来查询审计日志。

### 什么是 Controller

Controller 是“接电话的人”。它负责接 HTTP 请求，读取 query 参数、header 或 request body，然后把工作交给 service。

Phase 4 的 Controller 有两个：

```text
backend-java/src/main/java/com/xiaoc/workbench/event/api/EventController.java
backend-java/src/main/java/com/xiaoc/workbench/governance/api/AuditLogController.java
```

`EventController` 只负责打开 SSE 连接。它不直接查数据库，也不自己管理前端连接。

`AuditLogController` 只负责根据 query 参数选择查询方式。它不自己拼装审计记录。

### 什么是 Service

Service 是“真正办事的人”。复杂业务逻辑应该放在 service 里，而不是放在 controller 里。

Phase 4 的核心 service 是：

```text
backend-java/src/main/java/com/xiaoc/workbench/event/service/RuntimeEventService.java
backend-java/src/main/java/com/xiaoc/workbench/event/service/RuntimeEventStreamService.java
backend-java/src/main/java/com/xiaoc/workbench/governance/service/AuditLogService.java
```

你可以这样记：

```text
RuntimeEventService       负责把事件存进数据库，也负责从数据库回放事件
RuntimeEventStreamService 负责管理正在连接的 SSE 浏览器客户端
AuditLogService           负责记录和查询审计日志
```

### 什么是 Repository

Repository 是“数据库访问入口”。我们不在业务代码里到处写 SQL，而是通过 repository 读写表。

Phase 4 主要使用：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/repository/RuntimeEventRepository.java
backend-java/src/main/java/com/xiaoc/workbench/governance/repository/AuditLogRepository.java
```

一个访问 `runtime_events` 表，一个访问 `audit_logs` 表。

### 什么是 DTO

DTO 是“接口层传输对象”。数据库实体不一定适合直接返回给前端，所以我们会定义专门的返回结构。

Phase 4 有两个重要 DTO：

```text
RuntimeEventEnvelope
AuditLogSummary
```

`RuntimeEventEnvelope` 是 SSE data 里的事件包。

`AuditLogSummary` 是审计日志查询返回给前端的简化记录。

### 什么是事务

事务就是“要么一起成功，要么一起失败”。

例如 Runner 启动 run 时，会修改 run 状态、task 状态，还会写 runtime event 和 audit log。不能出现这种情况：

```text
run 已经变成 RUNNING
但是 event 没写进去
```

也不能出现这种情况：

```text
event 已经推给前端
但是数据库事务后来失败回滚了
```

所以 Phase 4 的原则是：

```text
先改业务状态
再写 runtime_events 表
事务提交成功后
再通过 SSE 推给前端
```

## 核心概念

### RuntimeEvent

RuntimeEvent 是“系统运行过程中发生的一件事”。

例如：

```text
project.created
run.started
task.completed
human_gate.waiting
human_gate.approved
run.completed
```

这些不是给开发者看的 debug 日志，而是给产品页面、时间线、运行记录看的业务事件。

一个 RuntimeEvent 最终会变成这种 JSON 包：

```json
{
  "id": "event-123",
  "run_id": "run-123",
  "event_type": "task.completed",
  "payload": {
    "task_id": "task-1",
    "node_id": "need_analysis",
    "status": "completed"
  },
  "created_at": "2026-06-14T10:00:00Z"
}
```

这个包叫 envelope。Envelope 可以理解成“信封”：外层字段说明这封信属于哪个 run、是什么事件、什么时候发生，`payload` 里放这类事件自己的细节。

### Payload

Payload 是事件的具体内容。

不同事件需要的细节不一样：

```text
run.started         需要 run_id、project_id、status
task.completed      需要 task_id、node_id、kind、status
human_gate.approved 需要 gate_id、reason、decided_by
```

如果每种事件都建一张表，系统会变得很重。Phase 4 选择把 payload 保存成 JSON 文本：

```text
runtime_events.payload
```

这样以后新增事件字段时，不一定要改表结构。

### SSE

SSE 全称是 Server-Sent Events。你可以把它理解成“服务器持续给浏览器发消息的一条 HTTP 连接”。

普通 HTTP 请求是这样：

```text
浏览器 -> 后端: 给我当前状态
后端 -> 浏览器: 这是当前状态
连接结束
```

SSE 是这样：

```text
浏览器 -> 后端: 我想订阅 run-123 的事件
后端 -> 浏览器: event-1
后端 -> 浏览器: event-2
后端 -> 浏览器: event-3
连接保持
```

浏览器不需要一直轮询。只要连接还在，后端可以持续推送。

### 为什么不用 WebSocket

WebSocket 是双向通信。浏览器能发消息给服务器，服务器也能发消息给浏览器。

Phase 4 只需要一个方向：

```text
后端 -> 前端
```

前端只是看运行事件，不需要通过这条连接控制 Runner。所以 SSE 更简单：

- 基于普通 HTTP。
- 浏览器原生支持 `EventSource`。
- 适合服务器单向推送。
- 比 WebSocket 更容易在本阶段讲清楚和测试。

这不是说 WebSocket 不好，而是当前需求不需要它。

### Replay

Replay 是“断线后补发错过的事件”。

假设前端已经收到：

```text
event-1
event-2
```

然后网络断了。断线期间后端又产生了：

```text
event-3
event-4
```

浏览器重连时会告诉后端：

```text
Last-Event-ID: event-2
```

后端就应该从数据库里找出 `event-2` 后面的事件：

```text
event-3
event-4
```

这就是 replay。

如果事件只存在内存里，服务重启后就没法 replay。所以 Phase 4 要先把事件存进数据库。

### AuditLog

AuditLog 是审计日志。它关注的是操作责任。

典型问题是：

```text
谁启动了这个 run？
谁批准了这个 HumanGate？
谁拒绝了这个 HumanGate？
这个 gate 被操作时给出的 reason 是什么？
```

AuditLog 的核心字段是：

```text
actor_id     谁做的
action       做了什么
target_type  对什么类型的对象
target_id    对哪个对象
payload      操作细节
created_at   什么时候做的
```

例如：

```json
{
  "actor_id": "local-user",
  "action": "HUMAN_GATE_APPROVE",
  "target_type": "human_gate",
  "target_id": "gate-123",
  "payload": {
    "run_id": "run-123",
    "reason": "scope confirmed"
  }
}
```

### RuntimeEvent 和 AuditLog 的区别

这是 Phase 4 最容易混淆的点。

RuntimeEvent 面向“运行时间线”：

```text
run started
task completed
human gate waiting
run completed
```

它主要服务前端展示、运行观察、断线 replay。

AuditLog 面向“操作追责”：

```text
local-user started run-123
local-user approved gate-123
local-user rejected gate-456
```

它主要服务审计、排查、责任链。

有些动作两边都会记录。例如 approve gate：

```text
RuntimeEvent: human_gate.approved
AuditLog:     HUMAN_GATE_APPROVE
```

前者告诉时间线“流程进入了批准状态”，后者告诉审计系统“某个 actor 做了批准操作”。

## 设计原因

### 为什么事件要先存数据库

如果先推 SSE，再存数据库，会有一个风险：

```text
前端已经看到事件
数据库保存失败
刷新页面后事件消失
```

这会让用户不信任系统。

Phase 4 的规则是：

```text
先保存 runtime_events
保存成功后等待事务提交
提交成功后再推 SSE
```

这样即使浏览器断线，也可以从数据库 replay。

### 为什么 SSE 发布要等事务提交

假设 Runner 正在一个事务里执行：

```text
task 改成 completed
runtime event 保存 task.completed
```

如果事件刚保存就立刻推给前端，但事务最后失败回滚，前端就会看到一个并不存在的完成事件。

所以 `RuntimeEventService` 在有事务时使用 Spring 的 transaction synchronization，等 commit 成功后再调用 SSE 发布。

### 为什么用 JSON envelope

如果直接把数据库实体返回给前端，会暴露太多内部结构，也不利于长期演进。

Envelope 的好处是：

- 外层字段稳定。
- `payload` 可以灵活扩展。
- 前端时间线可以统一处理不同事件。
- SSE `data` 和普通 replay 返回可以使用同一个结构。

### 为什么 audit action 用大写

RuntimeEvent 类型使用小写点分隔：

```text
task.completed
human_gate.approved
```

它更适合前端事件名和时间线展示。

AuditLog action 使用大写下划线：

```text
RUN_START
HUMAN_GATE_APPROVE
```

它更像后台操作记录，适合筛选、统计和告警。

### 为什么 Phase 4 不做登录和 RBAC

当前项目还是本地 demo 阶段。Phase 4 的重点是事件与审计基础设施，不是权限系统。

所以 actor 继续使用 Phase 3 的默认本地用户：

```text
local-user
```

真正登录、角色、权限、操作人身份校验，可以在权限阶段加入。现在提前做会让学习目标变散。

### 为什么不接 RabbitMQ 或 Redis

RabbitMQ 适合做后台任务队列。Redis Pub/Sub 或消息总线适合多实例事件广播。

但 Phase 4 的目标是：

```text
单机本地 demo 中，先把事件落库、回放、SSE 推送、审计查询跑通
```

如果现在引入 RabbitMQ 或 Redis，会多出环境依赖和分布式问题，不利于初学者掌握核心概念。

## 端到端流程

### 流程一：创建项目

用户调用：

```http
POST /api/projects
```

`ProjectApplicationService.createProject(...)` 会创建：

- project
- room
- run
- tasks
- task edges

Phase 4 增加两件事：

```text
写 runtime event: project.created
写 audit log: PROJECT_CREATE
```

这表示项目创建这件事可以在时间线和审计记录里被看到。

### 流程二：打开 SSE 事件流

前端想看某个 run 的实时事件，会调用：

```http
GET /api/events/stream?run_id=run-xxx
```

后端处理顺序是：

```text
EventController
-> RuntimeEventStreamService.open(runId, afterId)
-> 从数据库 replay 已有事件
-> 注册当前 SseEmitter
-> 后续新事件通过同一个 emitter 推给前端
```

如果前端带了：

```http
Last-Event-ID: event-123
```

或者 query 参数：

```http
GET /api/events/stream?run_id=run-xxx&after_id=event-123
```

后端只 replay `event-123` 后面的事件。

### 流程三：启动 run

用户调用：

```http
POST /api/runs/{runId}/start
```

Runner 开始推进 DAG。Phase 4 增加：

```text
写 runtime event: run.started
写 audit log: RUN_START
```

然后 Runner 执行 ready task。每完成一个普通 task，写：

```text
runtime event: task.completed
```

如果遇到 HumanGate，Runner 暂停并写：

```text
runtime event: human_gate.waiting
```

这时前端时间线能实时看到：

```text
项目已创建
run 已启动
需求分析任务已完成
正在等待人工确认
```

### 流程四：人工 approve

用户调用：

```http
POST /api/human-gates/{gateId}/approve
```

请求体可能是：

```json
{
  "reason": "scope confirmed",
  "decided_by": "local-user"
}
```

Runner 会把 gate 改成 approved，把 gate 对应的 task 改成 completed，然后继续执行后续任务。

Phase 4 记录：

```text
runtime event: human_gate.approved
runtime event: task.completed
audit log: HUMAN_GATE_APPROVE
```

如果后续所有任务都跑完，还会记录：

```text
runtime event: run.completed
audit log: RUN_COMPLETED
```

### 流程五：人工 reject

用户调用：

```http
POST /api/human-gates/{gateId}/reject
```

请求体可能是：

```json
{
  "reason": "scope too broad",
  "decided_by": "local-user"
}
```

Runner 会把 gate 改成 rejected，把 run 和 project 改成 failed。

Phase 4 记录：

```text
runtime event: human_gate.rejected
runtime event: run.failed
audit log: HUMAN_GATE_REJECT
audit log: RUN_FAILED
```

这说明流程不是静默失败，而是留下了明确证据。

### 流程六：查询审计日志

按操作人查询：

```http
GET /api/audit-logs?actor_id=local-user
```

按目标对象查询：

```http
GET /api/audit-logs?target_type=human_gate&target_id=gate-xxx
```

如果既没有 `actor_id`，也没有完整的 `target_type + target_id`，接口会返回 400，因为后端不知道你想查什么。

## 代码导读

### RuntimeEventEnvelope

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/event/api/RuntimeEventEnvelope.java
```

它是 SSE data 的统一结构。

重点字段：

```java
String id
String runId
String eventType
Map<String, Object> payload
Instant createdAt
```

注意 JSON 字段使用下划线：

```json
run_id
event_type
created_at
```

Java 代码里使用驼峰：

```java
runId
eventType
createdAt
```

这是后端常见做法：Java 代码按语言习惯写，API 输出按 JSON 习惯写。

### RuntimeEventService

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/event/service/RuntimeEventService.java
```

它做三件事：

1. `append(...)`：把事件写进数据库。
2. `replay(...)`：按 run ID 从数据库回放事件。
3. `toEnvelope(...)`：把数据库实体转换成 API envelope。

阅读建议：

```text
先看 append
再看 replay
最后看事务提交后的 publish
```

`append` 的关键思想是：

```text
Map payload
-> ObjectMapper 写成 JSON 字符串
-> RuntimeEvent 实体保存
-> 转成 RuntimeEventEnvelope
-> 事务提交后发布到 SSE
```

`replay` 的关键思想是：

```text
按 created_at 和 id 排序取出事件
如果 afterId 为空，返回全部
如果 afterId 存在，只返回它后面的事件
```

### RuntimeEventStreamService

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/event/service/RuntimeEventStreamService.java
```

它负责 SSE 连接。

你可以把它理解成一个“连接管理器”：

```text
run-1 -> emitter A, emitter B
run-2 -> emitter C
```

当 `run-1` 有新事件时，只推给订阅 `run-1` 的 emitter。

它主要做两件事：

1. `open(runId, afterId)`：创建 SSE 连接，先 replay，再注册连接。
2. `publish(event)`：把新事件发给当前订阅这个 run 的连接。

`SseEmitter` 是 Spring MVC 提供的对象。你不需要自己手写 HTTP chunk 格式，Spring 会帮你把事件写成 SSE 响应。

### EventController

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/event/api/EventController.java
```

接口：

```http
GET /api/events/stream?run_id=...
```

它读取两个可能的断点来源：

```text
query 参数 after_id
HTTP header Last-Event-ID
```

如果两者都有，优先使用 `after_id`。这对调试很方便，因为你可以在浏览器或 curl 里直接拼 query 参数。

### AuditLogSummary

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/governance/api/AuditLogSummary.java
```

它是审计日志查询返回结构。

核心字段：

```text
id
actor_id
action
target_type
target_id
payload
created_at
```

### AuditLogService

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/governance/service/AuditLogService.java
```

它做两类事情：

```text
record(...)       记录一条审计日志
findByActor(...)  按操作人查询
findByTarget(...) 按对象查询
```

审计日志也有 payload，因为不同 action 的细节不一样。例如 approve 需要 reason，run start 需要 run_id 和 project_id。

### AuditLogController

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/governance/api/AuditLogController.java
```

它支持两种查询：

```http
GET /api/audit-logs?actor_id=local-user
GET /api/audit-logs?target_type=human_gate&target_id=gate-123
```

如果参数不完整，会抛出 `IllegalArgumentException`。全局异常处理器会把它转成 HTTP 400。

### ApiExceptionHandler

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/common/web/ApiExceptionHandler.java
```

Phase 4 增加了对非法参数的处理：

```text
IllegalArgumentException -> 400 invalid_request
```

这样 audit 查询参数不完整时，前端能得到明确错误，而不是 500。

### SecurityConfig

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java
```

Phase 4 为本地 demo 放开：

```text
GET /api/events/stream
GET /api/audit-logs
```

这是为了保持本地学习项目可以直接访问。生产系统里，这两个接口都应该挂权限，因为事件和审计记录可能包含敏感信息。

### RunnerService

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/RunnerService.java
```

Phase 4 在 Runner 的关键状态变化处插入事件和审计。

重点找这些事件名：

```text
run.started
task.completed
human_gate.waiting
human_gate.approved
human_gate.rejected
run.completed
run.failed
```

阅读建议：

1. 先看 `startRun`，理解 run 启动时怎么写 `run.started` 和 `RUN_START`。
2. 再看 `advance`，理解普通 task 完成和 HumanGate waiting 事件。
3. 再看 `approveGate`，理解 approve 后怎么写事件、审计，并继续推进 DAG。
4. 最后看 `rejectGate`，理解 reject 为什么直接失败，不继续执行。

### ProjectApplicationService

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java
```

Phase 4 在创建项目后记录：

```text
runtime event: project.created
audit log: PROJECT_CREATE
```

这让整个 run 的时间线从项目创建开始，而不是从启动 run 才开始。

## 边界条件

### 浏览器断线重连

浏览器断线后，SSE 客户端可以带上最后收到的事件 ID。后端根据 `after_id` 或 `Last-Event-ID` replay 后续事件。

如果事件已经落库，即使服务端内存里的 emitter 没了，重连也能补数据。

### after_id 不存在

当前实现如果找不到 `after_id` 对应的事件，会返回空 replay。这样不会错误地把整条历史重复发给前端。

如果未来产品希望“找不到 after_id 时从头 replay”，可以改规则，但必须同步改测试和文档。

### 重复 start

Phase 3 已经处理了重复 start 的幂等性。Phase 4 需要注意不要因为重复 start 给一个已经暂停或结束的 run 重复写关键事件。

幂等性的意思是：同一个操作重复执行，结果不会越来越乱。

### approve 和 reject 的互斥

一个 HumanGate 只能 approve 或 reject 一次。已经 approved 的 gate 不能 reject，已经 rejected 的 gate 不能 approve。

Phase 4 的事件和审计也要遵守这个状态机，不能为非法状态转换写出误导性记录。

### SSE 不是永久可靠消息队列

SSE 连接会断，浏览器会重连，服务也可能重启。Phase 4 的可靠性来自数据库 replay，而不是内存里的 emitter。

这也是为什么本阶段不把内存列表当成事件源。

### 本地 demo 不是生产安全配置

当前接口允许本地无登录访问，是为了学习和演示。

生产环境中，至少需要考虑：

- 谁可以看某个 run 的事件。
- 谁可以查询 audit log。
- payload 里是否包含敏感字段。
- 审计日志是否需要防篡改。

这些是重要问题，但不属于 Phase 4 的核心实现。

## 测试说明

### 运行全部测试

在后端目录执行：

```powershell
cd backend-java
mvn test
```

如果你只想跑 Phase 4 相关测试，可以执行：

```powershell
mvn "-Dtest=RuntimeEventServiceTest,EventControllerTest,AuditLogControllerTest,RunnerEventAuditIntegrationTest" test
```

PowerShell 里建议给 `-Dtest=...` 加引号，因为逗号在 PowerShell 里有特殊含义。

### RuntimeEventServiceTest

文件：

```text
backend-java/src/test/java/com/xiaoc/workbench/event/service/RuntimeEventServiceTest.java
```

它验证：

- payload map 能保存成 JSON。
- 从数据库 replay 时能解析回 map。
- event 按顺序返回。
- `after_id` 能跳过旧事件。

### EventControllerTest

文件：

```text
backend-java/src/test/java/com/xiaoc/workbench/event/api/EventControllerTest.java
```

它验证：

- SSE 接口本地无需登录。
- `run_id` 能传给 stream service。
- `Last-Event-ID` 能作为 replay 断点。
- 响应类型是 `text/event-stream`。

### AuditLogControllerTest

文件：

```text
backend-java/src/test/java/com/xiaoc/workbench/governance/api/AuditLogControllerTest.java
```

它验证：

- 可以按 actor 查询 audit log。
- 可以按 target 查询 audit log。
- 参数不完整时返回 400。

### RunnerEventAuditIntegrationTest

文件：

```text
backend-java/src/test/java/com/xiaoc/workbench/orchestrator/service/RunnerEventAuditIntegrationTest.java
```

它验证端到端行为：

- 创建项目会写 `project.created` 和 `PROJECT_CREATE`。
- 启动 run 会写 `run.started` 和 `RUN_START`。
- approve 后会写 `human_gate.approved`、`run.completed` 和对应 audit。
- reject 后会写 `human_gate.rejected`、`run.failed` 和对应 audit。

这个测试比单元测试更接近真实业务，因为它会跑完整服务组合，而不是只测一个方法。

## 排错手册

### 问题一：SSE 接口返回 401

检查：

```text
backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java
```

确认本地 demo 放开了：

```text
GET /api/events/stream
```

如果 security 配置没放开，MockMvc 或浏览器访问会被拦截。

### 问题二：审计接口返回 400

先检查请求参数。

下面是合法请求：

```http
GET /api/audit-logs?actor_id=local-user
GET /api/audit-logs?target_type=human_gate&target_id=gate-123
```

下面是不完整请求：

```http
GET /api/audit-logs
GET /api/audit-logs?target_type=human_gate
GET /api/audit-logs?target_id=gate-123
```

不完整请求返回 400 是正确行为。

### 问题三：SSE 没有新事件

按顺序检查：

1. Runner 是否真的执行到了对应状态变化。
2. `RuntimeEventService.append(...)` 是否被调用。
3. `runtime_events` 表里是否有记录。
4. 事务是否提交成功。
5. `RuntimeEventStreamService.publish(...)` 是否收到事件。
6. 前端订阅的 `run_id` 是否和事件里的 `run_id` 一致。

最常见错误是订阅了错误的 run ID。

### 问题四：replay 结果为空

检查：

- `run_id` 是否正确。
- `after_id` 是否属于同一个 run。
- `after_id` 是否存在。
- 数据库里是否真的有该 run 的事件。

如果 `after_id` 不存在，当前规则是返回空 replay。

### 问题五：payload 解析失败

检查 `runtime_events.payload` 是否是合法 JSON。

Phase 4 使用 `ObjectMapper` 写入 payload，不应该手写 JSON 字符串。手写 JSON 容易漏引号、漏转义，导致读取时解析失败。

### 问题六：测试里缺少 ObjectMapper

某些 `@DataJpaTest` 只加载 JPA 相关 Bean，不会自动加载完整 Spring Boot 上下文。

如果测试需要 `RuntimeEventService`，而 service 需要 `ObjectMapper`，测试里可能要显式提供测试配置。

这不是业务代码的问题，而是测试切片加载范围的问题。

### 问题七：PowerShell 跑指定测试失败

如果执行：

```powershell
mvn -Dtest=RunnerServiceTest,ProjectApplicationServiceTest test
```

PowerShell 可能把逗号当成特殊语法。改成：

```powershell
mvn "-Dtest=RunnerServiceTest,ProjectApplicationServiceTest" test
```

## 面试讲法

可以这样介绍 Phase 4：

> Phase 4 我实现的是运行可观测性和审计基础。Phase 3 已经能创建项目、启动 run、执行 DAG、停在 HumanGate 并等待人工 approve 或 reject。Phase 4 在这些关键状态变化处写入 RuntimeEvent，并通过 SSE 提供给前端实时订阅。事件先持久化到 `runtime_events` 表，再在事务提交后推给 SSE 客户端，所以浏览器断线重连时可以通过 `after_id` 或 `Last-Event-ID` 从数据库 replay 未收到的事件。同时我把关键用户操作写入 AuditLog，例如创建项目、启动 run、approve/reject gate、run 完成或失败。RuntimeEvent 服务运行时间线，AuditLog 服务操作追责，两者职责分开。

如果面试官问为什么用 SSE：

> 当前阶段前端只需要接收后端的运行事件，不需要在同一条连接里双向通信。SSE 基于 HTTP，浏览器原生支持，适合服务器单向推送，也比 WebSocket 更容易测试和落地。后续如果出现双向协作、多人实时编辑或更复杂的交互，再考虑 WebSocket。

如果面试官问怎么保证事件不丢：

> 我没有把内存中的 SSE emitter 当成唯一事件源。每个 RuntimeEvent 会先写入数据库，SSE 只是实时推送通道。浏览器断线后可以带上最后收到的事件 ID，后端按 run ID 从数据库 replay 后续事件。这样服务重启或连接断开都不会导致历史事件不可恢复。

如果面试官问 audit log 和 runtime event 的区别：

> RuntimeEvent 面向运行过程展示，回答“系统发生了什么”，比如 task completed、human gate waiting。AuditLog 面向审计追责，回答“谁对什么对象做了什么操作”，比如 local-user approved gate-123。一次业务动作可能同时产生两类记录，但它们服务的读者和查询方式不同。

如果面试官问为什么现在不接 RabbitMQ：

> RabbitMQ 解决的是异步任务分发和后台 worker 问题，而 Phase 4 的核心是事件落库、SSE replay 和审计查询。当前本地 demo 先保持单机同步执行，把事件模型和边界跑通。队列抽象已经在 Phase 3 预留，后续接 RabbitMQ 时可以在 worker 推进 run 的同时继续调用同一套 RuntimeEventService 和 AuditLogService。

## 学习检查清单

你可以用下面的问题检查自己是否真的理解：

- 我能不能用一句话解释 RuntimeEvent 是什么？
- 我能不能说清楚为什么事件要先存数据库再推 SSE？
- 我能不能画出 `EventController -> RuntimeEventStreamService -> RuntimeEventService` 的调用关系？
- 我能不能解释 `after_id` 和 `Last-Event-ID` 的作用？
- 我能不能说清楚 SSE 和 WebSocket 的区别？
- 我能不能说清楚 RuntimeEvent 和 AuditLog 的区别？
- 我能不能找到 `RunnerService` 里写 `run.started` 的位置？
- 我能不能找到 `RunnerService` 里写 `human_gate.approved` 的位置？
- 我能不能解释为什么 reject 后会有 `run.failed`？
- 我能不能运行 Phase 4 的测试并看懂失败信息？
- 我能不能说出本阶段为什么不做 RBAC、RabbitMQ 和 Redis？
- 我能不能用面试语言讲清楚这一阶段的设计取舍？
