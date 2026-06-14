# Phase 4 Event + SSE + Audit Design

## Goal

Phase 4 connects the Phase 3 runner to an observable event and audit layer. The backend should persist runtime events as JSON envelopes, expose a Server-Sent Events stream that can replay missed events, and record audit logs for key local demo operations.

This phase stays on the Java golden path. It does not implement the broader production-enhancement list from the archived document, such as RBAC, field-level encryption, BudgetGuard, OpenTelemetry, Docker/nginx deployment, OMA recovery, or template marketplace features.

## Source Of Truth

The active project design defines Phase 4 as:

- `RuntimeEvent` JSON envelope.
- SSE replay.
- `AuditLog` key operations.
- Learning document: `docs/learning/phase-04-sse-event-audit.md`.

The current schema already contains `runtime_events` and `audit_logs`, so the implementation should wire existing persistence into services and APIs instead of adding a new migration.

## In Scope

- Add a runtime event envelope DTO with:
  - `id`
  - `run_id`
  - `event_type`
  - `payload`
  - `created_at`
- Add a `RuntimeEventService` that:
  - stores event payloads as JSON text in `runtime_events.payload`;
  - returns replayable envelopes with `payload` parsed back into an object;
  - publishes events to SSE clients only after the event has been stored.
- Add an SSE stream endpoint:
  - `GET /api/events/stream?run_id=...`
  - optional `after_id=...` query parameter;
  - optional `Last-Event-ID` header fallback;
  - response media type `text/event-stream`.
- Add a `RuntimeEventStreamService` using Spring MVC `SseEmitter`.
- Add an `AuditLogService` and audit DTOs.
- Add audit query endpoint:
  - `GET /api/audit-logs?actor_id=...`
  - `GET /api/audit-logs?target_type=...&target_id=...`
- Record runtime events and audit logs for:
  - project creation;
  - run start;
  - task completion;
  - human gate waiting;
  - human gate approval;
  - human gate rejection;
  - run completion;
  - run failure.
- Permit local demo access to the new event and audit endpoints.
- Add a beginner-friendly learning document for Phase 4.

## Out Of Scope

- No frontend EventTimeline implementation; this belongs to Phase 5.
- No real authentication or RBAC changes.
- No login audit because the current local demo has no login flow.
- No retry audit because the current runner has no retry API.
- No RabbitMQ, Redis, or distributed event bus.
- No migration from `runtime_events.payload text` to `jsonb`.
- No production-grade multi-node SSE fanout.

## API Contract

### SSE Stream

`GET /api/events/stream?run_id=run-123`

Behavior:

1. Load persisted events for the run in `(created_at, id)` order.
2. If `after_id` or `Last-Event-ID` is present, replay only events after that event ID.
3. Send each event as an SSE item:
   - SSE `id` is the runtime event ID.
   - SSE `event` is the event type.
   - SSE `data` is the JSON envelope.
4. Keep the emitter registered so later events for the same run can be pushed.
5. Remove emitters on completion, timeout, or send failure.

Example event data:

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

### Audit Query

`GET /api/audit-logs?actor_id=local-user`

Returns newest-first audit summaries for one actor.

`GET /api/audit-logs?target_type=human_gate&target_id=gate-123`

Returns newest-first audit summaries for one target.

The controller rejects requests that do not provide either an actor query or a complete target query.

## Event Types

Use lowercase dot-separated event types because they are readable in SSE clients and timeline UIs:

- `project.created`
- `run.started`
- `task.completed`
- `human_gate.waiting`
- `human_gate.approved`
- `human_gate.rejected`
- `run.completed`
- `run.failed`

## Audit Actions

Use uppercase action names because audit logs are operational records:

- `PROJECT_CREATE`
- `RUN_START`
- `HUMAN_GATE_APPROVE`
- `HUMAN_GATE_REJECT`
- `RUN_COMPLETED`
- `RUN_FAILED`

The default local actor remains `local-user`, matching the Phase 3 HumanGate API default.

## Transaction Rule

The design rule is:

```text
domain state update -> runtime event row stored -> transaction commits -> SSE publish
```

`RuntimeEventService` should save and flush the event row, then use Spring transaction synchronization to publish after commit when a transaction is active. If there is no active transaction, it may publish immediately after the save.

This keeps the key promise from the main design: the event stream can be replayed from the database, and a browser reconnect does not depend on in-memory history.

## Testing Strategy

Follow TDD.

Service and repository tests should prove:

- runtime event payload maps are serialized and parsed correctly;
- replay returns events in persisted order;
- replay after an event ID skips older events;
- runner operations create runtime events;
- runner operations create audit logs;
- idempotent repeated starts do not duplicate stopped-run events.

Controller tests should prove:

- the SSE endpoint is permitted without authentication for the local demo;
- the SSE controller forwards `run_id`, `after_id`, and `Last-Event-ID` correctly;
- audit queries work by actor and by target;
- invalid audit query parameters return HTTP 400.

Full verification remains:

```powershell
cd backend-java
mvn test
```

## Learning Document Requirements

`docs/learning/phase-04-sse-event-audit.md` must assume the reader is a beginner. It should explain:

- what an event is;
- why events must be stored before being streamed;
- what SSE is and why it is simpler than WebSocket for this phase;
- what replay means;
- what audit logs are;
- why runtime events and audit logs are different;
- where each Phase 4 class lives;
- how to test and debug this module;
- how to explain this design in an interview.
