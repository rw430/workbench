# Phase 03: Runner + HumanGate

## 怎么使用这份文档

这份文档是给“几乎没做过后端项目”的学习者看的。你不需要先懂 Spring Boot、队列、状态机、事务或 DAG。我们会先把每个概念讲成普通话，再把它放回代码里看。

建议阅读方式：

1. 先读“先建立最小后端概念”和“核心概念”，不用急着打开代码。
2. 再跟着“端到端流程”，想象一次请求怎样从 API 进入系统。
3. 最后读“代码导读”，一边打开文件一边对照。
4. 如果测试失败，先看“排错手册”。
5. 如果准备面试，重点看“面试讲法”。

Phase 2 做完后，系统已经能创建项目、房间、运行实例、任务和任务依赖。Phase 3 要解决的问题是：这些任务怎样真正往前走，以及在关键节点怎样停下来等人确认。

## 学习目标

学完这一阶段，你应该能回答这些问题：

- Runner 是什么，为什么它不是 Controller。
- Queue 抽象是什么，为什么现在还没接 RabbitMQ 也要留这个接口。
- DAG 里的任务为什么不能随便执行，必须看依赖是否完成。
- 状态机是什么，为什么 Project、Run、Task、HumanGate 都需要状态。
- HumanGate 为什么重要，它和普通自动任务有什么区别。
- approve 和 reject 对后续 DAG 的影响分别是什么。
- `POST /api/runs/{runId}/start` 做了什么。
- `POST /api/human-gates/{gateId}/approve` 做了什么。
- `POST /api/human-gates/{gateId}/reject` 做了什么。
- 如何用测试验证 Runner 和 HumanGate 行为。

## 业务背景

这个项目模拟的是“信用卡分期活动配置与审批系统研发协同”。用户输入一个目标后，系统会把目标拆成多个阶段：

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

Phase 2 只完成了“把这些任务创建出来”。它像是把路线图画好了，但还没有车沿着路线开。

Phase 3 增加 Runner。Runner 就是“执行推进器”：它会找出现在可以做的任务，执行它，保存输出，然后判断下一个任务是否可以开始。

但银行业务不能全自动往下跑。PRD 范围确认这一步必须停下来等人确认。这个停顿点就是 HumanGate。它的作用不是生成内容，而是让系统进入“等待人工确认”状态。

## 先建立最小后端概念

### 什么是 API

API 可以理解成“系统对外提供的按钮”。前端或者用户调用一个地址，后端就做一件明确的事。

Phase 3 新增三个按钮：

```http
POST /api/runs/{runId}/start
POST /api/human-gates/{gateId}/approve
POST /api/human-gates/{gateId}/reject
```

第一个按钮启动 run。第二个按钮表示人工确认通过。第三个按钮表示人工确认拒绝。

### 什么是 Controller

Controller 是“接电话的人”。它负责接收 HTTP 请求，取出路径参数或请求体，然后调用真正做事的 service。

Controller 不应该自己写复杂业务逻辑。比如它不应该自己判断哪个 task 能执行，也不应该自己修改一堆状态。否则后面测试、复用和维护都会变难。

Phase 3 的 Controller 文件是：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/RunController.java
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/HumanGateController.java
```

### 什么是 Service

Service 是“真正办事的人”。复杂业务规则放在 service 里。

Phase 3 的核心 service 是：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/RunnerService.java
```

它负责：

- 找到 run。
- 找到 project。
- 找到 tasks 和 edges。
- 判断哪些 task 可以执行。
- 执行普通 task。
- 遇到 HumanGate 时暂停。
- approve 后恢复。
- reject 后失败。

### 什么是 Repository

Repository 是“数据库操作入口”。后端不要到处写 SQL，而是通过 repository 读写实体。

Phase 3 会用到：

```text
OrchestratorRunRepository
OrchestratorTaskRepository
TaskEdgeRepository
HumanGateRepository
ProjectRepository
```

你可以把它们理解成不同表的“专用访问对象”。

### 什么是事务

事务就是“要么一起成功，要么一起失败”。

Runner 推进任务时，经常需要同时改很多东西：

- task 从 `READY` 变成 `COMPLETED`。
- 下游 task 从 `PENDING` 变成 `READY`。
- run 从 `RUNNING` 变成 `WAITING_HUMAN`。
- project 从 `RUNNING` 变成 `WAITING_HUMAN`。
- 新建一条 HumanGate。

这些修改不能只成功一半。Spring 的 `@Transactional` 就是为了保证这一点。

## 核心概念

### Runner

Runner 是 DAG 的推进器。

它每次做三件事：

1. 找出当前能执行的任务。
2. 执行任务并保存结果。
3. 根据结果改变后续任务状态。

在 Phase 3 里，Runner 不调用真实 LLM，也不调用真实 Runtime。它使用确定性执行器生成稳定文本。这样测试不会依赖外部服务，学习时也更容易理解。

### Queue 抽象

Queue 是任务队列。真实生产系统里，启动 run 通常不会在 HTTP 请求线程里一直执行，而是把 run ID 丢进 RabbitMQ 这类消息队列，由后台 worker 慢慢处理。

但 Phase 3 不直接接 RabbitMQ。原因是：现在更重要的是先把 Runner 和 HumanGate 的业务闭环跑通。

所以我们做了一个抽象：

```text
RunQueue
```

当前实现是：

```text
LocalRunQueue
```

它不发消息，只是直接调用 `RunnerService.startRun(runId)`。

好处是：Controller 面向 `RunQueue`，以后换成 RabbitMQ 版时，Controller 不需要改。

### DAG

DAG 是有向无环图。你可以先把它理解成“带依赖关系的任务列表”。

比如：

```text
need_analysis -> human_gate_prd -> risk_compliance_review
```

意思是：

- `need_analysis` 完成前，`human_gate_prd` 不能执行。
- `human_gate_prd` 完成前，`risk_compliance_review` 不能执行。

这就是为什么 Runner 不能简单地从头到尾乱跑。它必须检查依赖。

### 任务解锁

任务解锁指的是：当一个任务的所有前置依赖都完成后，它可以从 `PENDING` 变成 `READY`。

例子：

```text
human_gate_prd depends_on need_analysis
```

当 `need_analysis` 变成 `COMPLETED` 后，Runner 发现 `human_gate_prd` 的依赖全部完成，就可以把它变成 `READY`。

### 状态机

状态机就是“一个东西允许有哪些状态，以及状态之间怎样切换”。

比如一个 Task：

```text
PENDING -> READY -> RUNNING -> COMPLETED
```

遇到人工确认时：

```text
READY -> WAITING_HUMAN -> COMPLETED
```

被拒绝时：

```text
WAITING_HUMAN -> FAILED
```

状态机的价值是让系统行为清楚、可测试、可解释。

### HumanGate

HumanGate 是人工确认节点。

它不是“让 AI 生成文本”的任务，而是“让系统暂停，等人做决定”的任务。

在本项目中，PRD 范围确认是 HumanGate。因为进入风险评审和技术方案前，必须先确认需求边界。

HumanGate 有三个主要状态：

```text
WAITING
APPROVED
REJECTED
```

### 确定性执行器

确定性执行器指的是：输入相同 task，输出总是一样。

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/DeterministicTaskExecutor.java
```

它根据 task kind 生成固定文本，比如：

- `LLM_PRD_DRAFT` 生成 PRD 草稿摘要。
- `LLM_RISK_REVIEW` 生成风险评审摘要。
- `LLM_TECH_DESIGN` 生成技术方案摘要。

它现在不是为了替代 LLM，而是为了让后端主流程先稳定。

## 设计原因

### 为什么 Controller 不直接调用 RunnerService

Phase 3 选择：

```text
RunController -> RunQueue -> LocalRunQueue -> RunnerService
```

而不是：

```text
RunController -> RunnerService
```

原因是我们提前留出了队列边界。现在的 `LocalRunQueue` 只是同步调用，后面接 RabbitMQ 时可以换成：

```text
RabbitRunQueue -> RabbitMQ -> RunnerWorker -> RunnerService
```

Controller 不需要知道底层是本地执行还是消息队列。

### 为什么先本地实现

如果现在立刻接 RabbitMQ，会多出很多新问题：

- 本地环境要启动 RabbitMQ。
- 测试要加容器或 mock。
- 消息重复投递要处理。
- worker 异常重试要处理。
- 运行结果还要靠 SSE 或轮询查看。

这些都重要，但不是 Phase 3 的重点。Phase 3 的重点是 Runner 状态机和 HumanGate 决策。

### 为什么不生成 Artifact / Reflection / Lessons

DAG 模板里已经有 reflection 和 lessons 节点。Phase 3 会把这些节点当作普通确定性任务执行，保存 task output。

但真正的 Artifact、Reflection、Lessons 表级生成属于后续阶段。现在提前做会扩大边界，让 Phase 3 难以学习和测试。

### 为什么状态保存在数据库

Runner 执行不是一次性内存计算。它会暂停在 HumanGate。

如果状态只放在内存里，服务重启后就丢了。数据库里的状态能让系统恢复当前 run：

- 哪些任务完成了。
- 哪个任务正在等人确认。
- gate 是 waiting、approved 还是 rejected。
- run 是 running、waiting_human、completed 还是 failed。

## 端到端流程

### 流程一：创建项目后启动 run

第一步，Phase 2 的接口创建项目：

```http
POST /api/projects
```

响应里会有：

```json
{
  "run": {
    "id": "run-xxx",
    "status": "created"
  },
  "tasks": [
    {
      "node_id": "need_analysis",
      "status": "ready"
    },
    {
      "node_id": "human_gate_prd",
      "status": "pending"
    }
  ],
  "human_gate": null
}
```

第二步，启动 run：

```http
POST /api/runs/run-xxx/start
```

Controller 调用：

```text
RunQueue.enqueueStart(runId)
```

当前实际进入：

```text
LocalRunQueue.enqueueStart
-> RunnerService.startRun
```

Runner 先执行 `need_analysis`，然后解锁 `human_gate_prd`。因为 `human_gate_prd` 是人工确认节点，Runner 不继续往下跑，而是创建 HumanGate 并暂停。

响应变成：

```json
{
  "run": {
    "status": "waiting_human"
  },
  "tasks": [
    {
      "node_id": "need_analysis",
      "status": "completed"
    },
    {
      "node_id": "human_gate_prd",
      "status": "waiting_human"
    }
  ],
  "human_gate": {
    "status": "waiting"
  }
}
```

### 流程二：approve 后继续执行

当用户确认 PRD 范围没问题：

```http
POST /api/human-gates/gate-xxx/approve
```

请求体：

```json
{
  "reason": "scope confirmed",
  "decided_by": "local-user"
}
```

Runner 会：

1. 把 HumanGate 从 `WAITING` 改成 `APPROVED`。
2. 把 human gate task 改成 `COMPLETED`。
3. 把 run 和 project 改回 `RUNNING`。
4. 解锁后续任务。
5. 继续执行后续所有普通任务。
6. 全部完成后，把 run 和 project 改成 `COMPLETED`。

最终响应里 run 状态是：

```json
{
  "run": {
    "status": "completed"
  },
  "human_gate": {
    "status": "approved"
  }
}
```

### 流程三：reject 后终止

如果用户认为 PRD 范围不对：

```http
POST /api/human-gates/gate-xxx/reject
```

请求体：

```json
{
  "reason": "scope too broad",
  "decided_by": "local-user"
}
```

Runner 会：

1. 把 HumanGate 改成 `REJECTED`。
2. 把 human gate task 改成 `FAILED`。
3. 把 run 和 project 改成 `FAILED`。
4. 后续任务保持 `PENDING`，不会继续执行。

这体现了 HumanGate 的真正价值：关键风险点可以阻止错误流程继续扩大。

## 代码导读

### 入口一：启动 run

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/RunController.java
```

关键方法：

```java
@PostMapping("/{runId}/start")
public ProjectStateResponse startRun(@PathVariable String runId) {
    return runQueue.enqueueStart(runId);
}
```

这个方法很短，说明 Controller 没有承担业务逻辑。

### Queue 抽象

接口：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunQueue.java
```

当前实现：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/LocalRunQueue.java
```

`LocalRunQueue` 现在只是调用：

```java
runnerService.startRun(runId)
```

以后换 RabbitMQ 时，可以新增另一个实现，而不是改 Controller。

### Runner 核心逻辑

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/RunnerService.java
```

你可以重点看三个 public 方法：

```java
startRun(String runId)
approveGate(String gateId, String reason, String decidedBy)
rejectGate(String gateId, String reason, String decidedBy)
```

再看私有方法：

```java
advance(Project project, OrchestratorRun run)
```

它是 DAG 推进的核心。

阅读顺序建议：

1. 从 `startRun` 开始。
2. 看到它调用 `advance`。
3. 在 `advance` 里找 `READY` task。
4. 看普通 task 怎样 `complete`。
5. 看 `HUMAN_GATE` 怎样让 run 进入等待。
6. 回到 `approveGate`，看 approve 后怎样再次调用 `advance`。

### 状态迁移方法

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/domain/Project.java
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/domain/OrchestratorRun.java
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/domain/OrchestratorTask.java
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/domain/HumanGate.java
```

这些实体增加了类似方法：

```java
markRunning()
markWaitingHuman()
markCompleted()
markFailed()
```

好处是外部 service 不直接乱改字符串，而是通过有意义的方法表达业务动作。

### ProjectState 组装

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java
```

Phase 3 增加了：

```java
getRunState(String runId)
```

这样 Runner 执行完后，可以直接按 run ID 返回当前完整状态。

同时，`assembleState` 现在会把最新 HumanGate 映射到：

```json
"human_gate": {}
```

### HumanGate API

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/HumanGateController.java
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/HumanGateDtos.java
```

approve 和 reject 都调用 `RunnerService`，因为决策会影响 run、project、task 和 gate 的状态。

### 错误处理

文件：

```text
backend-java/src/main/java/com/xiaoc/workbench/common/web/InvalidStateException.java
backend-java/src/main/java/com/xiaoc/workbench/common/web/ApiExceptionHandler.java
```

如果一个 gate 已经 rejected，再 approve 就是非法状态。API 会返回 409。

409 的意思是：请求格式没错，但当前资源状态不允许这样操作。

## 边界条件

### 重复 start

如果 run 已经 `WAITING_HUMAN`，再次调用 start 不会重复执行已经完成的任务，也不会重复创建 gate。

这叫幂等性。幂等的意思是：同一个操作重复做，结果不会越来越乱。

### 重复 approve

如果 gate 已经 approved，再 approve 会返回当前状态，不会重复执行。

### rejected 后不能 approve

如果 gate 已经 rejected，再 approve 会返回 409。

这是为了防止一个失败流程被错误恢复。

### approved 后不能 reject

如果 gate 已经 approved，再 reject 会返回 409。

这是为了防止已经继续执行并完成的流程被反向改坏。

### 下游任务什么时候不执行

reject 之后，HumanGate task 变成 failed，run 变成 failed，下游 task 保持 pending。

这能清楚表达：不是系统忘了执行，而是被人工拒绝阻断了。

### 为什么没有并发锁

Phase 3 是本地单 JVM 同步执行，先不做分布式锁。后续接 RabbitMQ 和多 worker 时，需要补充“同一个 run 同时只能被一个 worker 推进”的保护。

## 测试说明

### 运行全部测试

在项目根目录执行：

```powershell
cd backend-java
mvn test
```

### RunnerService 测试

文件：

```text
backend-java/src/test/java/com/xiaoc/workbench/orchestrator/service/RunnerServiceTest.java
```

它覆盖：

- start run 后停在 HumanGate。
- waiting 状态下重复 start 不重复创建 gate。
- approve 后继续跑完所有任务。
- reject 后 run 失败，下游任务保持 pending。
- rejected 后 approve 会抛出 invalid state。
- approved 后 reject 会抛出 invalid state。

### Controller 测试

文件：

```text
backend-java/src/test/java/com/xiaoc/workbench/orchestrator/api/RunControllerTest.java
backend-java/src/test/java/com/xiaoc/workbench/orchestrator/api/HumanGateControllerTest.java
```

它们验证：

- 本地 demo 可以不登录访问。
- API 返回正确 JSON。
- invalid state 会映射为 409。

### Repository 测试

文件：

```text
backend-java/src/test/java/com/xiaoc/workbench/orchestrator/repository/OrchestratorRepositoryTest.java
```

它验证实体状态变化能正确写入数据库。

## 排错手册

### 问题一：run 启动后没有停在 HumanGate

先检查模板里的 human gate 节点：

```text
templates/dags/credit_card_installment_campaign_v1.yaml
```

确认 `human_gate_prd` 的 kind 是：

```text
HUMAN_GATE
```

再检查 `RunnerService.advance` 是否对 `HUMAN_GATE` 分支提前 return。

### 问题二：下游任务一直 pending

检查 task edge 是否正确保存。

重点看：

```text
TaskEdgeRepository.findAllByRunId
RunnerService.dependenciesComplete
```

如果依赖任务没有 `COMPLETED`，下游任务不会变成 `READY`。

### 问题三：approve 后没有继续执行

检查 `approveGate` 是否调用了：

```java
advance(project, run)
```

approve 不只是改 gate 状态，它还要恢复 runner。

### 问题四：reject 后后续任务被执行了

检查 `rejectGate` 是否只标记失败并直接返回 state。

reject 不应该调用 `advance`。

### 问题五：API 返回 401

检查：

```text
backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java
```

Phase 3 本地 demo 需要 permit：

```text
POST /api/runs/*/start
POST /api/human-gates/*/approve
POST /api/human-gates/*/reject
```

### 问题六：API 返回 409

409 通常说明你做了非法状态转换，比如：

- rejected 后又 approve。
- approved 后又 reject。

这不是 JSON 格式错误，而是业务状态不允许。

## 面试讲法

可以这样讲 Phase 3：

> Phase 3 我实现的是编排执行的第一版闭环。Phase 2 已经能创建 Project、Run、Task 和 Edge，但 DAG 还不会推进。Phase 3 增加了 RunnerService，按 DAG 依赖执行 READY 任务，并用确定性 executor 生成稳定输出。遇到 HUMAN_GATE 节点时，Runner 会暂停 run，创建 HumanGate 记录，并把 Project/Run/Task 状态置为 waiting。用户 approve 后，系统把 gate task 标记 completed，继续解锁和执行下游任务；用户 reject 后，run 失败，下游任务保持 pending。

如果面试官问为什么有 Queue：

> 我没有在 Controller 里直接调用 RunnerService，而是抽了 RunQueue。当前 LocalRunQueue 是同步本地实现，方便开发和测试。后续接 RabbitMQ 时，可以替换为消息队列实现，Controller 和 RunnerService 的边界不用改。

如果面试官问为什么不用真实 LLM：

> 这个阶段重点是状态机、DAG 推进和人工确认闭环。真实 LLM 会引入不稳定性和外部依赖，所以先用 deterministic executor 保证测试稳定。等流程稳定后，再把具体任务执行替换成 LLM 或 Runtime。

如果面试官问如何保证不会乱跑：

> Runner 每次只执行 READY task。PENDING task 必须等所有 upstream task 都 COMPLETED 后才会解锁。HumanGate 会把 run 停在 WAITING_HUMAN，只有 approve 才能继续，reject 会终止 run。

## 学习检查清单

你可以用下面问题检查自己是否真的理解：

- 我能不能说清楚 Runner 和 Controller 的区别？
- 我能不能说清楚 RunQueue 为什么存在？
- 我能不能画出 `start -> waiting_human -> approve -> completed` 的状态变化？
- 我能不能解释 reject 为什么不继续执行下游任务？
- 我能不能找到 `RunnerService.advance` 并说明它如何找 READY task？
- 我能不能说清楚 `PENDING` 和 `READY` 的区别？
- 我能不能解释为什么 Phase 3 不接 RabbitMQ？
- 我能不能运行 `mvn test` 并知道失败时先看哪类文件？
