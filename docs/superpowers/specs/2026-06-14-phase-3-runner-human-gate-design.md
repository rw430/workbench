# Phase 3 Runner + HumanGate Design

## Goal

Phase 3 turns the Phase 2 project skeleton into an executable backend workflow. A created project already has a run, tasks, task dependencies, and one `HUMAN_GATE` task in the DAG. This phase adds a runner that can move that DAG forward, stop at the human gate, accept approve or reject decisions, and resume or terminate the run deterministically.

The implementation must introduce a queue boundary so RabbitMQ can be added later without changing the runner API. The first implementation remains local and synchronous because the current project does not yet include RabbitMQ, SSE, or distributed workers.

## Approved Approach

Use a queue abstraction with a local implementation:

- `RunQueue` defines the queue-facing contract.
- `LocalRunQueue` executes the requested run immediately in the same JVM by delegating to `RunnerService`.
- REST APIs call the queue abstraction instead of calling the runner directly for run start.
- HumanGate approve and reject call the application service directly because they are user decisions, not background queue submissions.

This keeps the Phase 3 demo simple while preserving the future replacement point:

```text
Controller
-> RunQueue
-> LocalRunQueue
-> RunnerService
-> Repositories
-> ProjectStateResponse
```

Later RabbitMQ can replace `LocalRunQueue` with an implementation that publishes a run ID to a queue. `RunnerService` should not know whether it was called by HTTP, a local queue, or a RabbitMQ consumer.

## In Scope

- Add controlled status transition methods to existing entities.
- Add repository lookups needed by the runner and HumanGate decisions.
- Add deterministic task execution for non-human tasks.
- Add runner dependency logic:
  - execute `READY` tasks;
  - mark downstream tasks `READY` when all dependencies are `COMPLETED`;
  - stop at `HUMAN_GATE` and create one waiting `HumanGate`;
  - complete the run when all tasks are `COMPLETED`;
  - fail the run when a gate is rejected.
- Add APIs:
  - `POST /api/runs/{runId}/start`
  - `POST /api/human-gates/{gateId}/approve`
  - `POST /api/human-gates/{gateId}/reject`
- Return current `ProjectStateResponse` after each operation.
- Surface a waiting or decided `human_gate` in `ProjectStateResponse`.
- Add beginner-oriented learning documentation at `docs/learning/phase-03-runner-human-gate.md`.

## Out of Scope

- No RabbitMQ implementation.
- No SSE streaming.
- No `RuntimeEvent` emission.
- No audit log integration.
- No real LLM calls.
- No real external Runtime Daemon.
- No Artifact, Reflection, or Lessons generation.
- No concurrent worker locking beyond transactional state checks.

These items belong to later phases. The Phase 3 runner should make them easier to add, but should not implement them early.

## Domain Statuses

Persisted statuses remain uppercase. API DTO values remain lowercase.

Project and run statuses:

- `CREATED`
- `RUNNING`
- `WAITING_HUMAN`
- `COMPLETED`
- `FAILED`

Task statuses:

- `PENDING`
- `READY`
- `RUNNING`
- `WAITING_HUMAN`
- `COMPLETED`
- `FAILED`

HumanGate statuses:

- `WAITING`
- `APPROVED`
- `REJECTED`

## Runner Rules

When a run starts:

1. Load the run, project, room, tasks, and edges.
2. If the run is already `WAITING_HUMAN`, `COMPLETED`, or `FAILED`, do not re-execute completed work.
3. Set project and run to `RUNNING` when execution begins.
4. Execute ready non-human tasks deterministically:
   - mark task `RUNNING`;
   - generate stable output based on task kind, role, and name;
   - mark task `COMPLETED`;
   - unlock children whose dependencies are all complete.
5. When the next ready task is `HUMAN_GATE`:
   - mark task `WAITING_HUMAN`;
   - set project and run to `WAITING_HUMAN`;
   - create a `HumanGate` record if one does not already exist for the task;
   - return current project state.
6. When no tasks remain except completed tasks, set project and run to `COMPLETED`.

Approve behavior:

1. Load the gate and related task, run, and project.
2. If the gate is already approved, return current state without changing it.
3. If the gate is rejected, reject approval with an invalid-state error.
4. Mark gate `APPROVED` with reason, user, and decision time.
5. Mark the gate task `COMPLETED` and set project/run to `RUNNING`.
6. Unlock downstream tasks and continue runner execution until the next waiting gate or completion.

Reject behavior:

1. Load the gate and related task, run, and project.
2. If the gate is already rejected, return current failed state.
3. If the gate is approved, reject the rejection with an invalid-state error.
4. Mark gate `REJECTED` with reason, user, and decision time.
5. Mark the gate task `FAILED`.
6. Mark project and run `FAILED`.
7. Keep downstream tasks pending.

## API Contract

`POST /api/runs/{runId}/start`

- Request body: empty.
- Response: `ProjectStateResponse`.
- Typical first result: first task completed, human gate waiting, run status `waiting_human`.

`POST /api/human-gates/{gateId}/approve`

Request:

```json
{
  "reason": "PRD scope is confirmed.",
  "decided_by": "local-user"
}
```

Response: `ProjectStateResponse`.

`POST /api/human-gates/{gateId}/reject`

Request:

```json
{
  "reason": "Scope is too broad.",
  "decided_by": "local-user"
}
```

Response: `ProjectStateResponse`.

For local demo compatibility these endpoints are permitted without authentication, matching Phase 2 API behavior.

## Deterministic Executor

The executor should return stable text for each supported task kind:

- `LLM_PRD_DRAFT`: PRD draft summary.
- `LLM_RISK_REVIEW`: risk and compliance review.
- `LLM_TECH_DESIGN`: technical design summary.
- `LLM_TEST_PLAN`: test plan summary.
- `DELIVERY_SUMMARY`: delivery summary.
- `REFLECTION`: short reflection draft.
- `LESSONS_EXTRACT`: lessons draft.

The output can be plain text. It must be deterministic so tests and demos do not depend on real LLMs.

## Error Handling

Use existing error handling patterns and add an invalid-state response if needed.

- Missing run or gate returns 404.
- Approving a rejected gate returns 409.
- Rejecting an approved gate returns 409.
- Starting a missing run returns 404.

## Testing Strategy

Follow TDD.

Service integration tests should cover:

- starting a new run pauses at the first human gate and returns a waiting `human_gate`;
- starting a waiting run is idempotent;
- approving the gate resumes the run and completes all remaining tasks;
- rejecting the gate fails the run and keeps downstream tasks unexecuted;
- duplicate approval is idempotent;
- invalid decision transitions return a conflict error.

Controller tests should cover:

- local demo access without authentication;
- run start endpoint returns `waiting_human`;
- approve endpoint returns `completed`;
- reject endpoint returns `failed`;
- invalid state is mapped to HTTP 409.

Full verification remains:

```powershell
cd backend-java
mvn test
```

## Learning Document Requirements

The Phase 3 learning document must teach from zero-background assumptions:

- what a runner is;
- why a queue abstraction exists even before RabbitMQ;
- what a state machine is;
- how DAG dependency unlocking works;
- why HumanGate exists;
- how approve and reject differ;
- how to follow the API flow step by step;
- how to debug common state problems;
- how to explain the design in an interview.

It must include the same required learning sections used by previous modules.
