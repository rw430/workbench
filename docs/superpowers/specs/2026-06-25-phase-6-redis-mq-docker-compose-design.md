# Phase 6 Redis / MQ / Docker Compose Design

## Goal

Phase 6 turns the Phase 5 single-machine demo into a more realistic local engineering setup without changing the main run execution contract yet.

The phase must:

- Add Redis-backed infrastructure behavior that the current product can actually use.
- Add Docker Compose for local PostgreSQL, Redis, and RabbitMQ middleware.
- Keep the current `RunQueue` path local by default so the Phase 5 frontend flow still works.
- Reserve the RabbitMQ interface and message contract so a later phase can add a real producer and worker without rewriting `RunController` or `RunnerService`.
- Produce a beginner-friendly learning document that explains Redis, MQ, Docker Compose, and the exact design trade-offs.

## Non-Goals

Phase 6 must not implement a real RabbitMQ runner worker.

It must not change `POST /api/runs/{runId}/start` into an asynchronous "accepted only" API. The endpoint should continue to return `ProjectStateResponse` from the local queue path.

It must not require Redis or RabbitMQ for normal unit tests that do not exercise Redis-specific behavior. Tests should remain clear about which checks need Docker/Testcontainers.

It must not add production deployment concerns such as Kubernetes, TLS certificates, cloud secrets, or multi-node RabbitMQ clustering.

## Current Baseline

The current system already has the key queue boundary:

```text
RunController
-> RunQueue
-> LocalRunQueue
-> RunnerService
```

`RunController` does not call `RunnerService` directly. This is the right seam for later RabbitMQ support.

The current implementation has:

- `RunQueue`, an interface with `enqueueStart(String runId)`.
- `LocalRunQueue`, the default implementation that calls `RunnerService.startRun(runId)`.
- SSE event streaming and audit logging from Phase 4.
- A Phase 5 frontend that expects a synchronous `ProjectStateResponse` after starting a run.

Phase 6 should build on these boundaries instead of replacing them.

## Recommended Architecture

The Phase 6 runtime path stays local:

```text
RunController
-> RunStartRateLimiter
-> RunQueue
-> LocalRunQueue
-> RunConcurrencyGuard
-> RunnerService
```

Redis supports two concrete behaviors:

```text
Redis
  run lock: xiaoc:run-lock:{runId}
  rate limit: xiaoc:rate-limit:{actorOrIp}:{action}:{window}
```

RabbitMQ is introduced as a reserved contract:

```text
RunQueue
  LocalRunQueue       active in Phase 6
  RabbitRunQueue      reserved for later phase

RunStartMessage
  runId
  requestedBy
  requestedAt
  traceId
```

The RabbitMQ exchange, queue, routing key, and DTO names should be documented and configured, but no listener should consume run messages in Phase 6.

## Queue Design

`RunQueue` remains the API-facing abstraction.

`LocalRunQueue` remains the active implementation and must still return `ProjectStateResponse`.

RabbitMQ is reserved through:

- A queue mode property such as `xiaoc.queue.mode=local`.
- RabbitMQ naming properties:
  - exchange: `xiaoc.run`
  - queue: `xiaoc.run.start`
  - routing key: `run.start`
- A message DTO named `RunStartMessage`.
- Documentation explaining how `RabbitRunQueue` and `RunWorker` will be added later.

The important rule is that `RunnerService` should not know whether a run came from HTTP, local queue, or a future RabbitMQ worker.

## Redis Run Lock

The run lock protects against duplicate run advancement.

The lock key should include the run ID:

```text
xiaoc:run-lock:{runId}
```

The intended Redis operation is equivalent to:

```text
SET xiaoc:run-lock:{runId} {ownerToken} NX EX {ttlSeconds}
```

If the key already exists, the caller should fail with a clear conflict error instead of starting another runner flow for the same run.

The lock must have a TTL. A lock without a TTL can permanently block a run after a process crash.

For Phase 6, the guard can be placed inside `LocalRunQueue` before `RunnerService.startRun(runId)`:

```text
LocalRunQueue.enqueueStart(runId)
-> RunConcurrencyGuard.runWithLock(runId, callback)
-> RunnerService.startRun(runId)
```

This location is intentionally close to the execution boundary. A future RabbitMQ worker can reuse the same guard before calling `RunnerService`.

## Redis Rate Limiting

The rate limiter protects the run start API from repeated clicks or simple accidental flooding.

Phase 6 should implement a simple fixed-window counter rather than a complex token bucket. The learning value is high and the implementation remains understandable.

Example key:

```text
xiaoc:rate-limit:local-user:run-start:202606251650
```

Behavior:

1. Increment the counter for the current window.
2. Set TTL when the key is first created.
3. Allow the request when the count is within the configured limit.
4. Reject with a clear 429-style error when over the limit.

Suggested default:

```text
xiaoc.rate-limit.run-start.max-requests=20
xiaoc.rate-limit.run-start.window-seconds=60
```

The first implementation can identify the actor as `local-user` because the current demo security model is intentionally lightweight. The code should keep the actor key as an input so later authentication can supply a real user ID.

## Redis Availability Policy

Redis-backed behavior should be explicit, not hidden.

Recommended properties:

```text
xiaoc.redis.enabled=true
xiaoc.redis.fail-open=false
```

For local demo correctness, the default should be fail-fast when Redis is enabled but unavailable. That prevents a user from thinking run locks and rate limits are active when they are not.

Tests can override the property to disable Redis for cases unrelated to Phase 6.

## Docker Compose

`infra/docker-compose.yml` should provide local middleware:

```text
postgres
redis
rabbitmq
```

Recommended service ports:

```text
PostgreSQL: 5432
Redis:      6379
RabbitMQ:   5672
Management: 15672
```

The backend and frontend should remain outside Compose in Phase 6. Keeping them local avoids expanding the phase into Docker image builds, Windows bind mount details, hot reload behavior, Maven cache handling, and frontend dev server routing.

The runbook should explain:

- How to start middleware with `docker compose up -d`.
- How to inspect running services with `docker compose ps`.
- How to stop services with `docker compose down`.
- Which backend environment variables connect to Compose services.
- How to open RabbitMQ management UI.

## Backend Configuration

`application.yml` should include stable defaults:

```yaml
spring:
  data:
    redis:
      host: ${XIAOC_REDIS_HOST:localhost}
      port: ${XIAOC_REDIS_PORT:6379}
  rabbitmq:
    host: ${XIAOC_RABBITMQ_HOST:localhost}
    port: ${XIAOC_RABBITMQ_PORT:5672}
    username: ${XIAOC_RABBITMQ_USER:xiaoc}
    password: ${XIAOC_RABBITMQ_PASSWORD:xiaoc}

xiaoc:
  queue:
    mode: ${XIAOC_QUEUE_MODE:local}
  redis:
    enabled: ${XIAOC_REDIS_ENABLED:true}
    fail-open: ${XIAOC_REDIS_FAIL_OPEN:false}
  run-lock:
    ttl-seconds: ${XIAOC_RUN_LOCK_TTL_SECONDS:30}
  rate-limit:
    run-start:
      max-requests: ${XIAOC_RUN_START_RATE_LIMIT_MAX_REQUESTS:20}
      window-seconds: ${XIAOC_RUN_START_RATE_LIMIT_WINDOW_SECONDS:60}
  rabbitmq:
    run-start-exchange: ${XIAOC_RUN_START_EXCHANGE:xiaoc.run}
    run-start-queue: ${XIAOC_RUN_START_QUEUE:xiaoc.run.start}
    run-start-routing-key: ${XIAOC_RUN_START_ROUTING_KEY:run.start}
```

The exact Spring property class names can follow local style during implementation.

## Error Handling

Duplicate run start should return a clear conflict response.

Rate limit overflow should return a clear too-many-requests response.

Redis unavailable while enabled and fail-open is false should return a service-unavailable response or fail the application startup, depending on where the failure is detected.

The frontend does not need a Phase 6 redesign. It already renders API errors in the workbench. The backend error shape should reuse the project's existing error response patterns.

## Testing Strategy

Tests should cover behavior at three levels.

Unit tests:

- `RunConcurrencyGuard` grants the first lock and rejects a second lock.
- `RateLimitService` allows requests within a window and rejects requests over the limit.
- Queue mode configuration keeps `LocalRunQueue` as the default active implementation.

Integration tests:

- Redis-backed lock behavior against a real Redis Testcontainer.
- Redis-backed rate limit behavior against a real Redis Testcontainer.
- Existing runner tests still pass with local queue behavior.

Static infrastructure checks:

- `docker compose -f infra/docker-compose.yml config` validates the Compose file.
- The runbook documents all service ports and environment variables.

Full verification before completion:

```text
backend-java: mvn test
frontend: npm test
frontend: npm run build
infra: docker compose -f infra/docker-compose.yml config
repo: git diff --check
```

## Learning Document

Create:

```text
docs/learning/phase-06-redis-mq-docker-compose.md
```

The document must be beginner-friendly and explain:

- What Docker and Docker Compose are.
- Why a backend needs PostgreSQL, Redis, and RabbitMQ as separate services.
- What Redis is good at and why it is suitable for short locks and rate limits.
- What RabbitMQ is good at and why it is reserved but not activated in Phase 6.
- How the queue abstraction protects the controller from future implementation changes.
- How to start and inspect local middleware.
- How to explain this phase in an interview.

The document should avoid placeholder markers and should include troubleshooting steps for Docker daemon, occupied ports, Redis connection failure, and RabbitMQ login failure.

## Acceptance Criteria

Phase 6 is complete only when:

1. A Docker Compose file starts PostgreSQL, Redis, and RabbitMQ middleware.
2. Backend configuration contains Redis and RabbitMQ connection properties.
3. Local queue remains the default run execution path.
4. RabbitMQ message contract and configuration names are present, but no real RabbitMQ worker replaces local execution.
5. Redis run locking is implemented and tested.
6. Redis run start rate limiting is implemented and tested.
7. A Docker Compose runbook exists.
8. The Phase 6 learning document exists and is detailed enough for a beginner.
9. Backend tests, frontend tests, frontend build, Compose config validation, and whitespace checks pass.

## Future Phase

A later phase can add true RabbitMQ execution by implementing:

```text
RabbitRunQueue
-> RabbitMQ exchange
-> xiaoc.run.start queue
-> RunWorker
-> RunConcurrencyGuard
-> RunnerService
```

That later phase can then change `RunQueue.enqueueStart` to return an accepted state or split the command API from the state query API. Phase 6 deliberately avoids that semantic change.
