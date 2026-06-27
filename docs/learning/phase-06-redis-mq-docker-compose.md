# Phase 06: Redis / MQ / Docker Compose

## 怎么使用这份文档

这份文档写给“几乎没有后端工程基础”的读者。你不需要先会 Redis、RabbitMQ、Docker Compose，也不需要已经理解 Spring Boot 的所有自动配置。阅读时请把它当成一份带你拆项目的讲义，而不是背诵材料。

推荐顺序：

1. 先读“这一阶段解决什么问题”，知道为什么 Phase 6 不只是加几个依赖。
2. 再读“先建立最小概念”，把 Docker、Redis、RabbitMQ、队列、锁、限流这些词变成普通话。
3. 然后读“两条启动链路”，理解 local 模式和 rabbit 模式到底差在哪里。
4. 接着照着“本地运行练习”执行命令。只读不跑，很难真正建立感觉。
5. 最后打开“代码导读”里列出的文件，对照文档一段一段看源码。

如果你现在只想把项目跑起来，先看“本地运行练习”和“排错手册”。如果你要准备面试，重点看“设计取舍”和“面试讲法”。

常见误区：一上来就问“Redis 和 RabbitMQ 的所有原理是什么”。这会把学习范围拉得太大。本阶段只需要先理解它们在这个项目里分别解决了什么问题。

## 学习目标

读完这一阶段，你应该能回答这些问题：

- Docker Compose 是什么，为什么本地开发需要它。
- PostgreSQL、Redis、RabbitMQ 分别在这个项目里负责什么。
- 为什么 Controller 不直接调用 RabbitMQ，而是依赖 `RunQueue` 接口。
- `LocalRunQueue` 和 `RabbitRunQueue` 的差异是什么。
- 为什么第一版默认仍然用 local 模式，rabbit 模式通过配置打开。
- 什么是生产者、消费者、消息、交换机、队列、routing key。
- Redis run 锁解决什么问题，为什么锁必须有 TTL。
- Redis 限流解决什么问题，为什么不能只靠前端按钮禁用。
- `XIAOC_QUEUE_MODE=local` 和 `XIAOC_QUEUE_MODE=rabbit` 分别会启用哪些类。
- RabbitMQ 异步启动后，为什么前端需要通过 SSE 事件再刷新项目状态。
- 如何用测试证明 Redis、RabbitMQ、Compose 配置是可用的。
- 如果 Docker、Redis、RabbitMQ、端口、配置出问题，应该从哪里开始查。

常见误区：把“能跑起来”和“理解了”混为一谈。Phase 6 的重点不是多启动几个容器，而是理解一个后端系统怎样从单进程调用逐步走向更接近生产的异步架构。

## 这一阶段解决什么问题

Phase 5 结束后，前端已经可以点击 Start Run，后端也能推进一个 run。但是这个版本还有三个工程化问题：

第一，缺少统一的本地中间件环境。不同机器上可能有人本机装了 PostgreSQL，有人没装 Redis，有人 RabbitMQ 版本不同。项目越复杂，越不能靠“你自己装一下试试”。Docker Compose 让我们用一个文件声明本地需要哪些服务。

第二，启动 run 缺少跨进程保护。如果用户重复点击 Start Run，或者将来有多个 worker 同时处理同一个 run，同一个 run 可能被重复推进。前端按钮禁用只能减少误点，不能作为后端一致性保护。后端需要自己的 run 锁。

第三，长任务不应该永远和 HTTP 请求绑死。local 模式里，`POST /api/runs/{runId}/start` 会直接调用 `RunnerService.startRun`。这对第一版很简单，但真实系统常常会把“启动 run”这类命令发到消息队列，由后台 worker 消费。

Phase 6 的目标就是：

```text
本地环境: 用 Docker Compose 启动 PostgreSQL、Redis、RabbitMQ
并发保护: 用 Redis 给 run 加短锁
流量保护: 用 Redis 给启动 run 做限流
队列抽象: Controller 只依赖 RunQueue 接口
默认体验: local 模式仍然可用
扩展路径: rabbit 模式可以通过配置打开，由 RabbitMQ worker 异步执行
前端联动: SSE 收到运行事件后刷新最新 ProjectState
```

常见误区：以为“加 RabbitMQ”就等于把系统做复杂了。这里的关键不是 RabbitMQ 本身，而是 `RunQueue` 这个抽象。抽象让 Controller 不关心底层是本地调用还是消息队列。

## 先建立最小概念

### Docker 是什么

Docker 可以把一个服务和它需要的运行环境打包成容器。你可以先把容器理解成一个比较轻量的“小房间”：Redis 在 Redis 的房间里运行，RabbitMQ 在 RabbitMQ 的房间里运行，PostgreSQL 在 PostgreSQL 的房间里运行。

这带来一个好处：你不需要在 Windows 上手动安装每个中间件，也不需要担心某个服务把系统环境弄乱。

本项目里的 Compose 文件在：

```text
infra/docker-compose.yml
```

常见误区：把 Docker 当成虚拟机。你现在不需要深入虚拟化原理，只要先记住：Docker 帮我们用相同配置启动相同服务。

### Docker Compose 是什么

Docker 一次通常启动一个容器。Docker Compose 用一个 YAML 文件一次描述多个容器。

本阶段的 Compose 文件声明了三个服务：

```text
postgres  -> 保存项目、run、任务、人审、事件、审计、交付物等持久数据
redis     -> 保存短期状态，例如 run 锁和限流计数
rabbitmq  -> 保存待消费的 run 启动消息
```

启动命令是：

```powershell
docker compose -f infra/docker-compose.yml up -d
```

这里的 `-f` 表示指定 Compose 文件，`up` 表示启动，`-d` 表示后台运行。

常见误区：命令必须从仓库根目录执行。因为路径写的是 `infra/docker-compose.yml`，如果你在别的目录执行，就需要改成正确路径。

### Redis 是什么

Redis 是一个内存型数据存储。你可以先把它理解成一个速度很快的 key-value 表：

```text
key                         value
xiaoc:run-lock:run-1        owner-token
xiaoc:rate-limit:user:start 8
```

Redis 常用于保存短期状态。短期状态的特点是：很重要，但通常不需要永久保存很多年。例如锁、计数器、缓存。

本阶段 Redis 做两件事：

```text
run 锁: 防止同一个 run 被重复推进
限流: 防止同一个用户短时间内重复启动太多 run
```

常见误区：把 Redis 当成主数据库。这个项目的业务数据仍然在 PostgreSQL，Redis 只负责短期协调状态。

### RabbitMQ 是什么

RabbitMQ 是消息队列。你可以把它理解成一个可靠的“任务收件箱”。

在 local 模式里，启动 run 是这样：

```text
用户点击 Start Run
前端发 HTTP 请求
后端 Controller 收到请求
后端直接调用 RunnerService.startRun
```

在 rabbit 模式里，启动 run 是这样：

```text
用户点击 Start Run
前端发 HTTP 请求
后端 Controller 收到请求
后端把 RunStartMessage 发给 RabbitMQ
HTTP 请求较快返回
后台 RunWorker 从 RabbitMQ 拿消息
RunWorker 调用 RunnerService.startRun
```

这个变化叫异步。异步不是“不执行”，而是“先把任务交出去，真正执行发生在另一个流程里”。

常见误区：以为消息发出后页面应该立刻看到最终结果。rabbit 模式下，API 返回时 worker 可能还没处理完，所以前端需要通过 SSE 事件和 `GET /api/projects/{projectId}` 刷新最新状态。

### 队列里的几个名词

为了看懂 `RabbitRunQueueConfig`，先认识四个词：

```text
message      消息。这里是 RunStartMessage，里面有 runId、actorId、requestedAt、requestId。
producer     生产者。发送消息的一方，这里是 RabbitRunQueue。
consumer     消费者。接收并处理消息的一方，这里是 RunWorker。
queue        队列。消息排队的地方，这里默认叫 xiaoc.run.start。
exchange     交换机。RabbitMQ 里负责把消息路由到队列的组件，这里默认叫 xiaoc.run。
routing key  路由键。交换机根据它决定消息去哪，默认是 run.start。
```

你可以先记一个简单版本：

```text
RabbitRunQueue 把消息发到 exchange
exchange 根据 routing key 把消息放进 queue
RunWorker 监听 queue 并处理消息
```

常见误区：把 exchange 和 queue 混成一个东西。queue 是消息实际排队的地方，exchange 是消息进入 RabbitMQ 后的路由入口。

## 两条启动链路

### 为什么需要 RunQueue 接口

`RunController` 不应该知道底层到底是 local 还是 rabbit。它只需要表达业务动作：“我要启动这个 run”。

所以代码里定义了一个接口：

```java
public interface RunQueue {
    ProjectStateResponse enqueueStart(String runId);
}
```

接口像一个插座标准。Controller 只知道插座长什么样，不关心插座背后接的是本地服务还是消息队列。

常见误区：看到接口就觉得是“多写了一层”。这里的接口有实际价值：它让本地同步执行和 RabbitMQ 异步执行可以替换，而 Controller 不需要改。

### local 模式

local 模式是默认模式：

```yaml
xiaoc:
  queue:
    mode: ${XIAOC_QUEUE_MODE:local}
```

如果没有设置 `XIAOC_QUEUE_MODE`，后端默认启用 `LocalRunQueue`。

local 模式链路：

```text
RunController
  -> RateLimitService.checkAllowed
  -> RunQueue.enqueueStart
  -> LocalRunQueue
  -> RunConcurrencyGuard.runWithLock
  -> RunnerService.startRun
  -> 返回最新 ProjectStateResponse
```

优点：

```text
容易理解
容易调试
API 返回时通常已经拿到最新状态
适合作为第一版默认体验
```

代价：

```text
HTTP 请求和 run 执行绑在一起
任务变重后请求会变慢
不适合很多后台 worker 分摊任务
```

常见误区：认为 local 模式没有价值。实际上第一版保留 local 很重要，因为它让开发和演示更稳定，也让后续 RabbitMQ 问题可以被隔离排查。

### rabbit 模式

rabbit 模式通过环境变量打开：

```powershell
$env:XIAOC_QUEUE_MODE="rabbit"
cd backend-java
mvn spring-boot:run
```

rabbit 模式链路：

```text
RunController
  -> RateLimitService.checkAllowed
  -> RunQueue.enqueueStart
  -> RabbitRunQueue
  -> RabbitTemplate.convertAndSend
  -> RabbitMQ exchange / queue
  -> RunWorker.handle
  -> RunConcurrencyGuard.runWithLock
  -> RunnerService.startRun
  -> SSE 推送运行事件
  -> 前端收到事件后调用 getProject 刷新状态
```

优点：

```text
HTTP 请求不用一直等 worker 做完
将来可以扩展多个 worker
失败、重试、削峰等能力更容易演进
更接近真实后端系统
```

代价：

```text
理解成本更高
本地必须启动 RabbitMQ
API 返回时可能还不是最终状态
前端必须适应异步刷新
```

常见误区：打开 rabbit 模式后，还期待 `startRun` 的响应立刻包含 HumanGate。rabbit 模式下真正推进 run 的是 worker，前端要看 SSE 和后续刷新。

## Redis run 锁

### 问题是什么

假设用户连续点两次 Start Run，或者两个 worker 同时拿到同一个 run 的启动消息。如果没有保护，可能出现：

```text
同一个 task 被执行两次
重复写 RuntimeEvent
重复创建 HumanGate
run 状态被两个流程交叉更新
```

这类问题不应该交给前端解决。前端可以禁用按钮，但网络重试、多个浏览器、多个 worker、脚本请求都能绕过前端。

### 锁怎么工作

`RedisRunConcurrencyGuard` 使用 Redis 的 `setIfAbsent`：

```text
如果 xiaoc:run-lock:{runId} 不存在，就写入 ownerToken，并设置 TTL
如果 key 已经存在，说明另一个流程正在推进这个 run，本次请求失败
```

ownerToken 是一个随机 UUID。释放锁时，不是简单删除 key，而是用 Lua 脚本确认 key 里的值仍然是自己的 token，再删除。

为什么要这么谨慎？因为锁可能过期后被别人重新拿到。如果旧流程这时直接删除 key，可能误删别人的锁。

常见误区：认为拿锁就是写一个 key，释放时删掉就行。真正要注意的是“只释放自己的锁”。

### 为什么锁需要 TTL

TTL 是 Time To Live，意思是这个 key 最多活多久。这里默认：

```yaml
xiaoc:
  run-lock:
    ttl-seconds: ${XIAOC_RUN_LOCK_TTL_SECONDS:30}
```

如果进程拿到锁后崩溃，没有 TTL 的锁会永远留在 Redis，之后这个 run 永远无法再推进。

有 TTL 后，即使进程崩溃，锁也会在一段时间后自动过期。

常见误区：TTL 设置得越长越安全。TTL 太长会让故障恢复很慢；TTL 太短可能任务还没执行完锁就过期。本阶段任务短，所以默认 30 秒适合开发环境。

## Redis 限流

### 问题是什么

如果某个用户或脚本在一分钟内发出大量 Start Run 请求，后端会被压垮，也可能制造大量重复数据。限流就是给某类动作加一个速度上限。

本项目目前用固定窗口限流：

```yaml
xiaoc:
  rate-limit:
    run-start:
      max-requests: ${XIAOC_RUN_START_RATE_LIMIT_MAX_REQUESTS:20}
      window-seconds: ${XIAOC_RUN_START_RATE_LIMIT_WINDOW_SECONDS:60}
```

意思是：同一个 actor 在 60 秒窗口内最多启动 20 次 run。

### 固定窗口怎么理解

可以把时间切成一个个 60 秒格子：

```text
第 100 个窗口: 允许计数 1 到 20
第 101 个窗口: 重新从 1 开始
```

`RedisRateLimitService` 生成类似这样的 key：

```text
xiaoc:rate-limit:local-user:run-start:窗口编号
```

每次请求让 Redis 对这个 key 自增。如果超过上限，就抛出 `RateLimitExceededException`，API 返回 429。

常见误区：只在前端做防抖就够了。防抖改善用户体验，但真正的限流必须在后端，因为后端才是所有请求都会经过的地方。

## Docker Compose 服务说明

### PostgreSQL

PostgreSQL 是关系型数据库，保存长期业务数据。本项目默认连接：

```text
jdbc:postgresql://localhost:5432/xiaoc_workbench
username: xiaoc
password: xiaoc
```

Compose 里的服务名是 `postgres`，容器名是 `xiaoc-postgres`。

常见误区：以为容器内端口和宿主机端口是同一个概念。`"5432:5432"` 的左边是你电脑上的端口，右边是容器里的端口。

### Redis

Redis 暴露在：

```text
localhost:6379
```

后端配置：

```yaml
spring:
  data:
    redis:
      host: ${XIAOC_REDIS_HOST:localhost}
      port: ${XIAOC_REDIS_PORT:6379}
```

常见误区：Redis 容器运行了就代表应用一定连上了。还要看后端配置的 host、port，以及 Docker 容器是否健康。

### RabbitMQ

RabbitMQ 有两个端口：

```text
5672   -> AMQP 协议端口，后端程序用这个端口发消息、收消息
15672  -> Management UI，浏览器打开 http://localhost:15672 查看队列
```

默认账号：

```text
username: xiaoc
password: xiaoc
```

后端配置：

```yaml
spring:
  rabbitmq:
    host: ${XIAOC_RABBITMQ_HOST:localhost}
    port: ${XIAOC_RABBITMQ_PORT:5672}
    username: ${XIAOC_RABBITMQ_USER:xiaoc}
    password: ${XIAOC_RABBITMQ_PASSWORD:xiaoc}
```

常见误区：把 `15672` 填到后端配置里。后端 AMQP 连接用的是 `5672`，`15672` 只是网页管理后台。

## 配置开关怎么读

Spring Boot 的 YAML 里经常出现这种写法：

```yaml
mode: ${XIAOC_QUEUE_MODE:local}
```

它的意思是：

```text
如果环境变量 XIAOC_QUEUE_MODE 存在，就用环境变量的值
如果不存在，就用 local
```

所以默认模式是 local。你只有显式设置环境变量时才进入 rabbit 模式。

本阶段重要开关：

```text
XIAOC_QUEUE_MODE=local                 使用 LocalRunQueue
XIAOC_QUEUE_MODE=rabbit                使用 RabbitRunQueue 和 RunWorker
XIAOC_REDIS_ENABLED=true               启用 Redis 锁和限流
XIAOC_REDIS_FAIL_OPEN=false            Redis 不可用时返回错误
XIAOC_RABBITMQ_ENABLED=true            启用 RabbitMQ 配置
XIAOC_RUN_LOCK_TTL_SECONDS=30          run 锁过期时间
XIAOC_RUN_START_RATE_LIMIT_MAX_REQUESTS=20
XIAOC_RUN_START_RATE_LIMIT_WINDOW_SECONDS=60
```

常见误区：修改了 PowerShell 环境变量后，已经启动的 Java 进程会自动改变。不会。环境变量是在进程启动时读取的，改完后要重启后端。

## 代码导读

### RunController

位置：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/RunController.java
```

它负责接收启动 run 的 HTTP 请求。Phase 6 后，它先调用限流：

```text
RateLimitService.checkAllowed("local-user", "run-start")
```

然后调用：

```text
RunQueue.enqueueStart(runId)
```

它不直接调用 `RunnerService`。这是本阶段的关键变化。

常见误区：看到 Controller 代码变少，就觉得功能少了。实际是职责更清楚了：Controller 管 HTTP，Queue 管启动方式。

### RunQueue

位置：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunQueue.java
```

这是启动 run 的统一接口。无论底层怎么实现，都要提供：

```text
enqueueStart(String runId)
```

常见误区：把接口理解成“空代码”。接口表达的是依赖边界，它告诉其他代码“你只能依赖这个动作，不要依赖具体实现”。

### LocalRunQueue

位置：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/LocalRunQueue.java
```

它只在 local 模式启用：

```java
@ConditionalOnProperty(name = "xiaoc.queue.mode", havingValue = "local", matchIfMissing = true)
```

核心逻辑是：

```text
拿 run 锁
调用 RunnerService.startRun
释放 run 锁
返回 ProjectStateResponse
```

常见误区：认为 local 模式不需要锁。即使是 local 模式，也可能有重复 HTTP 请求，所以仍然需要锁保护。

### RabbitRunQueue

位置：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueue.java
```

它只在 rabbit 模式启用：

```java
@ConditionalOnProperty(name = "xiaoc.queue.mode", havingValue = "rabbit")
```

它做两件事：

```text
创建 RunStartMessage
用 RabbitTemplate 把消息发到 RabbitMQ
```

然后它返回当前 run 状态。注意：这时 worker 可能还没处理完消息。

常见误区：把 `RabbitRunQueue` 当成真正执行 run 的地方。它是生产者，只负责发消息；真正执行的是 `RunWorker`。

### RunWorker

位置：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunWorker.java
```

它监听 RabbitMQ 队列：

```java
@RabbitListener(queues = "${xiaoc.rabbitmq.run-start-queue}")
```

收到 `RunStartMessage` 后：

```text
拿 run 锁
调用 RunnerService.startRun
如果锁已被别人拿到，就记录日志并跳过
```

常见误区：认为一个消息一定只会被处理一次，所以不需要锁。真实系统里可能有重投递、重复消息、手动重试，所以 worker 仍然要有幂等和并发保护意识。

### RabbitRunQueueConfig

位置：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueueConfig.java
```

它声明 RabbitMQ 的 exchange、queue、binding、message converter。你可以先这样理解：

```text
exchange: 消息入口
queue: 消息排队地点
binding: exchange 和 queue 的连接规则
message converter: Java 对象和 JSON 消息之间的转换器
```

常见误区：忘记声明 queue 和 binding。生产者能发消息不代表消费者一定能收到，消息必须能被路由到正确队列。

### RedisRunConcurrencyGuard

位置：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RedisRunConcurrencyGuard.java
```

它封装“拿锁、执行、释放锁”。这样 `LocalRunQueue` 和 `RunWorker` 都不用自己写 Redis 细节。

常见误区：在业务代码里到处散落 Redis 锁逻辑。那样会很难统一修复锁释放、TTL、异常处理。

### RedisRateLimitService

位置：

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RedisRateLimitService.java
```

它封装固定窗口限流。Controller 不知道 Redis key 怎么拼，只知道：

```text
checkAllowed(actorId, action)
```

常见误区：把限流写死在 Controller 里。抽成服务后，测试和替换都会更容易。

### 前端异步刷新

位置：

```text
frontend/src/App.tsx
frontend/src/api.ts
```

rabbit 模式下，后端 API 返回时 worker 可能还没处理完。所以前端收到这些 SSE 事件时，会重新拉取项目状态：

```text
run.started
task.completed
human_gate.waiting
human_gate.approved
human_gate.rejected
run.completed
run.failed
```

调用的是：

```text
GET /api/projects/{projectId}
```

常见误区：只把事件追加到时间线，不刷新 ProjectState。时间线会动，但 DAG、HumanGate、交付物可能还是旧状态。

## 本地运行练习

### 第一步：启动中间件

在仓库根目录运行：

```powershell
docker compose -f infra/docker-compose.yml up -d
```

检查：

```powershell
docker compose -f infra/docker-compose.yml ps
```

你应该看到 PostgreSQL、Redis、RabbitMQ 三个服务。

### 第二步：用 local 模式启动后端

local 是默认模式：

```powershell
cd backend-java
mvn spring-boot:run
```

这个模式最适合初学者先跑通，因为请求路径短，问题少。

### 第三步：启动前端

另开一个终端：

```powershell
cd frontend
npm run dev -- --host 127.0.0.1 --port 5173
```

浏览器打开：

```text
http://127.0.0.1:5173/
```

### 第四步：切换到 rabbit 模式

停止后端，重新启动：

```powershell
$env:XIAOC_QUEUE_MODE="rabbit"
cd backend-java
mvn spring-boot:run
```

然后打开 RabbitMQ 管理后台：

```text
http://localhost:15672
```

账号：

```text
xiaoc / xiaoc
```

点击 Start Run 后，你可以观察队列和事件变化。

常见误区：在同一个 PowerShell 里已经 `cd backend-java` 后又执行 `cd backend-java`，会进入错误路径。先看当前目录，再决定是否需要 `cd`。

## 测试说明

本阶段不是只靠手动点页面验证。测试分几层：

```text
单元测试: 验证某个类的行为，例如 LocalRunQueue、RunWorker、RedisRateLimitService
集成测试: 用 Testcontainers 启动真实 Redis、RabbitMQ、PostgreSQL
前端测试: 用 FakeEventSource 模拟 SSE，验证异步事件会刷新页面状态
Compose 验证: 用 docker compose config 确认 YAML 能被 Docker Compose 正确解析
```

常用命令：

```powershell
cd backend-java
mvn test
```

```powershell
cd frontend
npm test
```

```powershell
docker compose -f infra/docker-compose.yml config
```

常见误区：只跑自己新增的一个测试就认为完成。新增功能可能影响旧路径，最终仍然要跑完整后端和前端测试。

## 设计取舍

### 为什么默认 local

默认 local 有三个理由：

```text
学习成本低: 初学者可以先不理解 RabbitMQ 也能跑通项目
演示稳定: 少一个中间件依赖，现场演示更不容易出问题
保留退路: rabbit 模式出问题时，可以切回 local 判断是队列问题还是业务逻辑问题
```

常见误区：觉得默认 local 代表 RabbitMQ 没有实现。这里 RabbitMQ 路径已经存在，只是默认不强迫每次启动都依赖它。

### 为什么 Redis fail-open 默认 false

`XIAOC_REDIS_FAIL_OPEN=false` 表示 Redis 不可用时，锁和限流不假装成功，而是返回明确错误。

开发阶段这样更好，因为你能马上发现 Redis 没启动或配置错了。

如果 fail-open 是 true，Redis 出错时系统会继续执行，这在某些降级场景有用，但会失去锁和限流保护。

常见误区：把 fail-open 当成“更稳定”。它只是让请求更容易继续执行，不代表数据一致性更安全。

### 为什么前端要刷新 ProjectState

SSE 事件适合告诉前端“发生了什么”，但页面需要的是完整状态。例如 HumanGate 面板需要 gate id、question、status、reason 等字段。事件 payload 不一定包含完整状态。

所以前端做两步：

```text
收到事件 -> 追加 EventTimeline
如果事件会改变状态 -> 调用 getProject(projectId) 拉完整 ProjectState
```

常见误区：把 SSE 当成数据库同步。SSE 是通知通道，不应该承载所有页面状态。

## 排错手册

### Docker daemon 连接失败

现象：

```text
Cannot connect to the Docker daemon
```

处理：

```powershell
docker info
```

如果也失败，启动 Docker Desktop，等它完全启动后再运行 Compose 命令。

### 端口冲突

现象：

```text
port is already allocated
```

常见冲突端口：

```text
5432  PostgreSQL
6379  Redis
5672  RabbitMQ AMQP
15672 RabbitMQ Management UI
```

处理方式：停止占用端口的服务，或者修改 `infra/docker-compose.yml` 左侧宿主机端口。

### Redis 连接失败

现象：后端启动或请求时报 Redis unavailable。

检查：

```powershell
docker compose -f infra/docker-compose.yml ps
```

确认 `xiaoc-redis` 正在运行，并且端口是 `6379:6379`。

### RabbitMQ 登录失败

浏览器打开：

```text
http://localhost:15672
```

使用：

```text
xiaoc / xiaoc
```

如果刚启动就登录失败，等一会再试。RabbitMQ 容器启动到管理后台可用需要一点时间。

### rabbit 模式启动后没有 worker 效果

检查顺序：

```text
1. 后端启动前是否设置了 XIAOC_QUEUE_MODE=rabbit
2. RabbitMQ 容器是否运行
3. RabbitMQ 管理后台是否能看到 xiaoc.run.start 队列
4. 前端是否收到 SSE 事件
5. 前端是否调用 GET /api/projects/{projectId} 刷新状态
```

常见误区：只看 startRun API 的响应。rabbit 模式真正推进状态的是 worker，应该结合日志、SSE、项目状态一起看。

## 面试讲法

可以这样讲 Phase 6：

```text
我没有让 Controller 直接依赖 RabbitMQ，而是先抽象了 RunQueue。
默认 local 模式仍然同步调用 RunnerService，保证第一版开发和演示稳定。
当 XIAOC_QUEUE_MODE=rabbit 时，RabbitRunQueue 作为生产者把 RunStartMessage 发到 RabbitMQ，
RunWorker 作为消费者监听队列并调用 RunnerService。
Redis 在两条路径里都提供 run 级短锁，避免同一个 run 被重复推进；
Redis 也提供启动 run 的固定窗口限流，防止重复点击或脚本请求冲击后端。
Docker Compose 提供 PostgreSQL、Redis、RabbitMQ 的本地中间件环境。
rabbit 模式改变了 API 的时间语义，所以前端收到 SSE 状态变更事件后会重新拉取 ProjectState。
```

如果面试官追问“为什么不一步到位只用 RabbitMQ”，可以回答：

```text
因为项目需要稳定的第一版开发路径。local 模式更容易调试，也能作为 RabbitMQ 出问题时的对照组。
抽象 RunQueue 后，两种模式可以并存，Controller 不需要知道实现细节。
```

如果面试官追问“Redis 锁有什么风险”，可以回答：

```text
锁必须有 TTL，防止进程崩溃后永远锁住；
释放锁时要校验 owner token，避免误删别的执行者后来拿到的锁；
锁的 TTL 要结合任务耗时设置，太短会提前过期，太长会影响故障恢复。
```

常见误区：面试时只说“用了 Redis 和 RabbitMQ”。更好的说法是解释它们各自解决的问题、代码边界，以及你保留 local 模式的工程原因。

## 学习检查清单

读完后请尝试自己回答：

- 我能画出 local 模式从前端点击到 `RunnerService.startRun` 的链路。
- 我能画出 rabbit 模式从前端点击到 `RunWorker.handle` 的链路。
- 我能解释 `RunQueue` 接口为什么让 Controller 更稳定。
- 我能解释 Redis run 锁为什么要有 TTL 和 owner token。
- 我能解释固定窗口限流的 key 大概长什么样。
- 我能说明 RabbitMQ 的 exchange、queue、routing key 分别是什么。
- 我能说出 `5672` 和 `15672` 两个端口的区别。
- 我能用 Docker Compose 启动 PostgreSQL、Redis、RabbitMQ。
- 我能切换 `XIAOC_QUEUE_MODE=local` 和 `XIAOC_QUEUE_MODE=rabbit`。
- 我能解释 rabbit 模式下前端为什么要在 SSE 事件后刷新 ProjectState。
- 我能根据错误现象判断是 Docker、端口、Redis、RabbitMQ、后端配置还是前端刷新问题。

如果这些问题还答不上来，不要急着背定义。回到“本地运行练习”，边跑命令边看对应代码，学习效果会更稳。
