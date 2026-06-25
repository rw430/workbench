# Phase 6 Redis / MQ / Docker Compose Design

## Goal

Phase 6 turns the Phase 5 single-machine demo into a more realistic local engineering setup by introducing Redis-backed protection, Docker Compose middleware, and a real RabbitMQ runner queue.

The phase must:

- Add Redis-backed infrastructure behavior that the current product can actually use.
- Add Docker Compose for local PostgreSQL, Redis, and RabbitMQ middleware.
- Keep the current `LocalRunQueue` implementation available as a fallback and test-friendly mode.
- Add a real RabbitMQ-backed queue mode with a producer and worker while preserving the `RunQueue` abstraction.
- Produce a beginner-friendly learning document that explains Redis, MQ, Docker Compose, and the exact design trade-offs.

## Non-Goals

Phase 6 must not remove `LocalRunQueue`.

It must not make RabbitMQ the only way to run the system. Developers must still be able to run the local queue path without RabbitMQ.

It must not require the frontend to understand RabbitMQ directly. The frontend continues to call HTTP APIs and consume HTTP/SSE state.

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

`RunController` does not call `RunnerService` directly. This is the right seam for Phase 6 RabbitMQ support.

The current implementation has:

- `RunQueue`, an interface with `enqueueStart(String runId)`.
- `LocalRunQueue`, the default implementation that calls `RunnerService.startRun(runId)`.
- SSE event streaming and audit logging from Phase 4.
- A Phase 5 frontend that expects a synchronous `ProjectStateResponse` after starting a run.

Phase 6 should build on these boundaries instead of replacing them. The important change is that `RunQueue` now has two real modes:

```text
local  -> execute immediately inside the request path
rabbit -> publish a message and let a background worker execute later
```

## Recommended Architecture

The Phase 6 runtime path supports two modes.

Local mode:

```text
RunController
-> RunStartRateLimiter
-> RunQueue
-> LocalRunQueue
-> RunConcurrencyGuard
-> RunnerService
```

Rabbit mode:

```text
RunController
-> RunStartRateLimiter
-> RunQueue
-> RabbitRunQueue
-> RabbitMQ exchange
-> xiaoc.run.start queue
-> RunWorker
-> RunConcurrencyGuard
-> RunnerService
```

Redis supports two concrete behaviors:

```text
Redis
  run lock: xiaoc:run-lock:{runId}
  rate limit: xiaoc:rate-limit:{actorOrIp}:{action}:{window}
```

RabbitMQ is introduced as a real execution path:

```text
RunQueue
  LocalRunQueue       fallback mode
  RabbitRunQueue      RabbitMQ publishing mode

RunStartMessage
  runId
  requestedBy
  requestedAt
  traceId
```

The RabbitMQ exchange, queue, routing key, DTO, publisher, and listener should be implemented and tested in Phase 6.

## Queue Design

`RunQueue` remains the API-facing abstraction.

`LocalRunQueue` remains available and must still return `ProjectStateResponse`.

RabbitMQ is implemented through:

- A queue mode property such as `xiaoc.queue.mode=local` or `xiaoc.queue.mode=rabbit`.
- RabbitMQ naming properties:
  - exchange: `xiaoc.run`
  - queue: `xiaoc.run.start`
  - routing key: `run.start`
- A message DTO named `RunStartMessage`.
- `RabbitRunQueue`, which publishes `RunStartMessage`.
- `RunWorker`, which consumes `RunStartMessage` and calls `RunnerService`.

The important rule is that `RunnerService` should not know whether a run came from HTTP, local queue, or the RabbitMQ worker.

### Start API Semantics

Local mode can keep the existing synchronous behavior:

```text
POST /api/runs/{runId}/start
-> LocalRunQueue
-> RunnerService.startRun(runId)
-> ProjectStateResponse after immediate local advancement
```

Rabbit mode is asynchronous:

```text
POST /api/runs/{runId}/start
-> RabbitRunQueue publishes RunStartMessage
-> API returns the current ProjectStateResponse after enqueue
-> RunWorker later advances the run
-> frontend sees progress through SSE and refreshes
```

The API should not block waiting for RabbitMQ consumption. The response should clearly represent "message accepted and current state returned", not "all work completed".

The frontend should not need a structural redesign because Phase 5 already has SSE and can display current project state. Tests should be updated so the frontend does not assume that clicking start always synchronously advances every task in Rabbit mode.

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

For Phase 6, the guard should be used by both local and Rabbit worker execution paths:

```text
LocalRunQueue.enqueueStart(runId)
-> RunConcurrencyGuard.runWithLock(runId, callback)
-> RunnerService.startRun(runId)

RunWorker.handle(message)
-> RunConcurrencyGuard.runWithLock(message.runId(), callback)
-> RunnerService.startRun(message.runId())
```

This location is intentionally close to the execution boundary. It protects against duplicate HTTP requests in local mode and duplicate delivery or parallel workers in Rabbit mode.

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

Rabbit mode should be runnable locally by starting middleware with Compose and then launching the backend with:

```text
XIAOC_QUEUE_MODE=rabbit
```

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
    enabled: ${XIAOC_RABBITMQ_ENABLED:true}
    run-start-exchange: ${XIAOC_RUN_START_EXCHANGE:xiaoc.run}
    run-start-queue: ${XIAOC_RUN_START_QUEUE:xiaoc.run.start}
    run-start-routing-key: ${XIAOC_RUN_START_ROUTING_KEY:run.start}
```

The exact Spring property class names can follow local style during implementation.

## Error Handling

Duplicate run start should return a clear conflict response when the execution lock is already held.

Rate limit overflow should return a clear too-many-requests response.

Redis unavailable while enabled and fail-open is false should return a service-unavailable response or fail the application startup, depending on where the failure is detected.

RabbitMQ unavailable in `rabbit` mode should fail fast when publishing cannot happen. A run start request must not pretend that the run has been queued when the broker did not accept the message.

Rabbit worker failures should be visible through logs and should avoid infinite tight retry loops. Phase 6 can use RabbitMQ listener defaults plus clear error logging; advanced dead-letter queues can be documented as future work.

The frontend does not need a Phase 6 redesign. It already renders API errors in the workbench. The backend error shape should reuse the project's existing error response patterns.

## Testing Strategy

Tests should cover behavior at three levels.

Unit tests:

- `RunConcurrencyGuard` grants the first lock and rejects a second lock.
- `RateLimitService` allows requests within a window and rejects requests over the limit.
- Queue mode configuration keeps `LocalRunQueue` available and chooses the correct `RunQueue` implementation.
- `RabbitRunQueue` publishes the expected `RunStartMessage`.
- `RunWorker` delegates consumed messages to `RunnerService` through `RunConcurrencyGuard`.

Integration tests:

- Redis-backed lock behavior against a real Redis Testcontainer.
- Redis-backed rate limit behavior against a real Redis Testcontainer.
- Existing runner tests still pass with local queue behavior.
- RabbitMQ publisher and worker behavior against a real RabbitMQ Testcontainer.
- Rabbit mode start API enqueues and returns current state without blocking for completion.

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
- What RabbitMQ is good at and how Phase 6 uses producer, exchange, queue, routing key, and worker.
- How the queue abstraction protects the controller from future implementation changes.
- Why `LocalRunQueue` still exists after adding RabbitMQ.
- Why Rabbit mode changes timing semantics even though the frontend still calls the same HTTP API.
- How to start and inspect local middleware.
- How to explain this phase in an interview.

The document should avoid placeholder markers and should include troubleshooting steps for Docker daemon, occupied ports, Redis connection failure, and RabbitMQ login failure.

## Acceptance Criteria

Phase 6 is complete only when:

1. A Docker Compose file starts PostgreSQL, Redis, and RabbitMQ middleware.
2. Backend configuration contains Redis and RabbitMQ connection properties.
3. Local queue remains available as a supported run execution path.
4. RabbitMQ mode implements a real producer and worker while preserving the `RunQueue` abstraction.
5. Redis run locking is implemented and tested.
6. Redis run start rate limiting is implemented and tested.
7. A Docker Compose runbook exists.
8. The Phase 6 learning document exists and is detailed enough for a beginner.
9. Backend tests, frontend tests, frontend build, Compose config validation, and whitespace checks pass.

## Future Phase

A later phase can harden RabbitMQ execution with:

```text
dead-letter exchange
retry queue
worker concurrency tuning
message idempotency table
operations dashboard
```

That later phase can also split the command API from the state query API if Rabbit mode becomes the only production mode. Phase 6 keeps both local and Rabbit modes so learning and testing remain manageable.
