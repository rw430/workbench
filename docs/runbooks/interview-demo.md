# Interview Demo Runbook

## Purpose

Use this runbook when preparing or running a live interview demo of the Xiaoc workbench. It prioritizes a stable local queue demo first, then shows rabbit queue mode only when there is enough time and Docker middleware is healthy.

## Before The Interview

Run these checks at least 15 minutes before the interview:

```powershell
git status --short --branch
docker info
docker compose -f infra/docker-compose.yml config
```

Expected:

```text
git status shows a clean branch.
docker info exits 0.
docker compose config prints normalized configuration.
```

Recommended browser tabs:

```text
http://127.0.0.1:5173/
http://localhost:15672/
```

RabbitMQ login:

```text
username: xiaoc
password: xiaoc
```

## Start Middleware

From the repository root:

```powershell
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
```

Expected services:

```text
xiaoc-postgres healthy
xiaoc-redis healthy
xiaoc-rabbitmq healthy
```

If RabbitMQ is still starting, wait and rerun:

```powershell
docker compose -f infra/docker-compose.yml ps
```

## Local Queue Demo

Use local mode as the default live demo path.

### Start Backend

Open terminal 1:

```powershell
$env:XIAOC_QUEUE_MODE="local"
cd backend-java
mvn spring-boot:run
```

Wait for:

```text
Tomcat started on port 8889
Started XiaocWorkbenchApplication
```

### Start Frontend

Open terminal 2:

```powershell
cd frontend
npm run dev -- --host 127.0.0.1 --port 5173
```

Open:

```text
http://127.0.0.1:5173/
```

### Demo Steps

Use this goal:

```text
请帮我设计一个银行信用卡分期活动配置与审批系统，要求支持活动规则配置、审批流、风险校验、灰度发布和操作审计。
```

Click through:

```text
Analyze
Create Project
Start Run
Approve HumanGate
Refresh Audit
```

Expected observations:

```text
Intent panel shows banking credit-card campaign analysis.
Agent panel shows PD, DEV, QA, RISK, PMO-style roles.
DAG shows task nodes and dependencies.
Start Run stops at HumanGate.
Event timeline shows run and task events.
Approve completes the run.
Artifact, reflection, and lessons appear.
Audit panel shows approval/audit records.
```

## Rabbit Queue Demo

Use this only after the local queue demo succeeds.

### Restart Backend In Rabbit Mode

Stop the local backend with `Ctrl+C`.

Open terminal 1:

```powershell
$env:XIAOC_QUEUE_MODE="rabbit"
cd backend-java
mvn spring-boot:run
```

Wait for:

```text
Attempting to connect to: [localhost:5672]
Created new connection
Tomcat started on port 8889
```

### Observe RabbitMQ

Open:

```text
http://localhost:15672/
```

Look for queue:

```text
xiaoc.run.start
```

### Demo Steps

In the frontend, create a new project and click Start Run.

Expected difference from local mode:

```text
The immediate Start Run response may still show run status created.
The backend worker consumes RunStartMessage from RabbitMQ.
The frontend receives SSE events and refreshes ProjectState.
The UI then reaches HumanGate and can be approved.
```

What to say:

```text
This mode changes the time semantics. The HTTP request enqueues work; the worker advances the run asynchronously.
That is why the frontend listens for runtime events and pulls the latest project state after state-changing events.
```

## What To Say While Clicking

Before clicking Analyze:

```text
I start from an unstructured business goal and convert it into structured intent.
```

Before clicking Create Project:

```text
This creates the project model, run, task DAG, and dependencies.
```

Before clicking Start Run:

```text
Runner will advance the DAG until it reaches a node requiring human confirmation.
```

Before approving HumanGate:

```text
This is the control point. The approval is recorded, then Runner continues to produce the final deliverables.
```

When showing events:

```text
RuntimeEvent is for live observability; AuditLog is for accountability.
```

When showing Phase 6:

```text
Redis protects run execution with a short lock and rate limiter. RabbitMQ is the asynchronous queue mode. Docker Compose makes the middleware reproducible.
```

## Expected States

Local mode:

```text
Create Project -> project.status = created
Start Run -> run.status = waiting_human
Approve -> run.status = completed
Approve -> project.status = completed
```

Rabbit mode:

```text
Create Project -> project.status = created
Start Run immediate response -> run.status may still be created
Worker consumes message -> run.status = waiting_human
Approve -> run.status = completed
```

## Fallback Plan

### If Docker Is Not Ready

Say:

```text
The full middleware demo depends on Docker Compose. I will switch to explaining the architecture and test evidence.
```

Show:

```text
docs/learning/phase-06-redis-mq-docker-compose.md
backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueueIntegrationTest.java
```

### If Rabbit Mode Fails

Say:

```text
I will keep the live demo on local queue mode because it proves the business workflow. Rabbit mode is covered by integration tests and can be explained from code.
```

Show:

```text
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunQueue.java
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueue.java
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunWorker.java
```

### If Frontend Fails

Use API fallback:

```powershell
$goal = "请帮我设计一个银行信用卡分期活动配置与审批系统，要求支持活动规则配置、审批流、风险校验、灰度发布和操作审计。"
$body = @{ goal = $goal } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8889/api/projects" -ContentType "application/json" -Body $body
```

Then explain that the frontend is a visual workbench over the same API workflow.

## Shutdown

Stop frontend and backend with `Ctrl+C`.

Stop middleware:

```powershell
docker compose -f infra/docker-compose.yml down
```

Reset middleware data only when you want a clean database:

```powershell
docker compose -f infra/docker-compose.yml down -v
```

The `down -v` command deletes local PostgreSQL, Redis, and RabbitMQ volumes for this project.
