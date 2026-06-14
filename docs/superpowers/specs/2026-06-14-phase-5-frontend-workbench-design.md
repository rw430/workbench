# Phase 5 Frontend Workbench Design

## Goal

Phase 5 turns the existing React MVP into a usable TypeScript workbench for the Java backend golden path. The first screen should be the real operational workspace, not a landing page. A user should be able to enter the credit-card installment campaign development goal, analyze intent, see recommended agents, create a project, start the runner, approve or reject the HumanGate, observe SSE runtime events, inspect audit logs, and read generated delivery/reflection/lesson output.

## Source Of Truth

The active project design defines Phase 5 as:

- React workbench.
- Multi-page or multi-section layout.
- Smart home route.
- Project detail and DAG.
- HumanGate panel.
- EventTimeline.
- Artifact / Reflection / Lessons.
- Learning document: `docs/learning/phase-05-frontend-workbench.md`.

The user selected the operational workbench layout direction and chose to add minimal backend data for Artifact / Reflection / Lessons instead of showing empty placeholders.

## Current State

The frontend already exists under `frontend/` and uses:

- React 18.
- TypeScript.
- Vite.
- Vitest.
- Testing Library.
- lucide-react.

The current MVP is a single `App.tsx` that can create a project, render agents, render task tiles, show a HumanGate confirmation button, and subscribe to an old event type list.

The current MVP is not aligned with the Phase 4 backend contract:

- It calls `POST /api/human-gates/{gateId}/confirm`, but the backend now exposes `approve` and `reject`.
- It treats IDs as numbers, but backend DTOs use string IDs.
- It listens for old event names such as `run.created` and `run.task.completed`; Phase 4 emits `project.created`, `run.started`, `task.completed`, `human_gate.waiting`, `human_gate.approved`, `human_gate.rejected`, `run.completed`, and `run.failed`.
- It does not call `/api/intent/analyze`.
- It does not call `/api/agents/recommend` before project creation.
- It does not start runs through `POST /api/runs/{runId}/start`.
- It does not query `/api/audit-logs`.
- It has no real Lessons display.

## In Scope

### Frontend

Build an operational workbench layout with these regions:

- Left rail:
  - goal input;
  - intent analysis result;
  - recommended agent team.
- Main work area:
  - project/run status strip;
  - run start control;
  - DAG board with task statuses and outputs;
  - selected task output preview.
- Right rail:
  - HumanGate approve/reject panel;
  - SSE EventTimeline;
  - AuditLog panel.
- Bottom delivery section:
  - Artifact;
  - Reflection;
  - Lessons.

The frontend should support this golden path:

1. User edits or accepts the sample goal.
2. User analyzes intent.
3. User reviews intent and recommended agents.
4. User creates a project.
5. User starts the run.
6. Runner stops at HumanGate.
7. User approves or rejects the HumanGate.
8. UI updates from returned ProjectState and SSE events.
9. UI queries audit logs for `local-user`.
10. UI shows generated Artifact / Reflection / Lessons when a run completes.

The UI should be dense, quiet, and work-focused. It should feel like an internal engineering/SaaS operations tool, not a marketing page. Cards should be used for repeated entities and framed tool surfaces only. Do not put cards inside cards.

### Backend

Add a minimal deterministic delivery data loop:

- When a run completes, generate one `Artifact` row.
- When a run completes, generate one `Reflection` row.
- When a run completes, generate several `Lesson` rows.
- Include lessons in `ProjectStateResponse`.
- Continue returning existing `artifact` and `reflection` strings for compatibility, but populate them from persisted rows.

This backend addition is intentionally narrow. It exists so the Phase 5 workbench can show real data without waiting for a later LLM or growth engine phase.

### Documentation

Create `docs/learning/phase-05-frontend-workbench.md` for beginners. It should explain:

- what a frontend workbench is;
- how React state maps to backend state;
- why TypeScript types matter;
- how API calls are organized;
- how SSE differs from polling in the UI;
- how HumanGate approve/reject affects the screen;
- how Artifact / Reflection / Lessons are displayed;
- how to test and debug the frontend;
- how to explain the Phase 5 design in an interview.

## Out Of Scope

- No authentication UI or JWT flow.
- No real RBAC.
- No project list API unless the backend already supports it.
- No React Router dependency unless needed later.
- No WebSocket.
- No real LLM integration.
- No charting library.
- No drag-and-drop DAG editor.
- No template marketplace.
- No Docker Compose changes; that belongs to Phase 6.
- No RabbitMQ or Redis implementation; that belongs to Phase 6.

## UX Architecture

Use a single-screen operational workspace for Phase 5.

The user lands directly on the tool. The page starts with the sample banking goal already filled in. The first action is "Analyze", not a marketing CTA. After analysis, the same screen reveals intent details and recommended agents. Project creation and run execution happen from the same workspace.

The layout uses three primary columns on desktop:

```text
Left rail              Main work area                         Right rail
Goal + Intent + Agent  Run status + DAG + task output          HumanGate + Events + Audit
```

On mobile, the same sections stack vertically in this order:

```text
Goal
Intent
Agents
Run controls
DAG
HumanGate
EventTimeline
AuditLog
Delivery
```

The UI should use direct operational labels:

- `Analyze`
- `Create Project`
- `Start Run`
- `Approve`
- `Reject`
- `Refresh Audit`

Do not add visible tutorial text, keyboard shortcut explanations, or feature marketing copy inside the app.

## API Contract

### Intent

`POST /api/intent/analyze`

Request:

```json
{
  "goal": "..."
}
```

Response fields used by the frontend:

- `mode`
- `template_id`
- `domain`
- `risk_level`
- `human_gate_required`
- `confidence`
- `candidate_roles`

### Agent Recommendation

`POST /api/agents/recommend`

Request:

```json
{
  "goal": "..."
}
```

Response is a list of:

- `id`
- `name`
- `role`
- `skills`
- `score`
- `recommendation_reason`

### Project Creation

`POST /api/projects`

Request:

```json
{
  "goal": "..."
}
```

Response is `ProjectState`.

### Run Start

`POST /api/runs/{runId}/start`

Response is `ProjectState`.

### HumanGate

`POST /api/human-gates/{gateId}/approve`

`POST /api/human-gates/{gateId}/reject`

Request:

```json
{
  "reason": "scope confirmed",
  "decided_by": "local-user"
}
```

Response is `ProjectState`.

### SSE

`GET /api/events/stream?run_id={runId}`

Each SSE item uses:

- event id: runtime event ID;
- event name: runtime event type;
- data: `RuntimeEventEnvelope`.

The frontend should parse `event.data` as JSON and store an `EventTimelineItem`.

### AuditLog

`GET /api/audit-logs?actor_id=local-user`

Response is a list of:

- `id`
- `actor_id`
- `action`
- `target_type`
- `target_id`
- `payload`
- `created_at`

## Frontend State Model

`App` owns workflow-level state:

- `goal`
- `intent`
- `recommendedAgents`
- `projectState`
- `selectedTaskId`
- `events`
- `auditLogs`
- `busyAction`
- `error`

State transitions:

```text
idle
-> analyzing intent
-> intent ready
-> creating project
-> project created
-> starting run
-> waiting human or completed
-> approving/rejecting gate
-> completed or failed
```

The frontend should treat `ProjectState` returned from backend commands as the source of truth. SSE adds timeline detail, but should not be the only source for task/run status.

## Component Plan

Create focused components:

- `components/GoalPanel.tsx`
  - Owns the textarea display and action buttons via props.
- `components/IntentPanel.tsx`
  - Shows mode, template, domain, risk, confidence, candidate roles.
- `components/AgentPanel.tsx`
  - Shows recommended agents or project agents.
- `components/RunControl.tsx`
  - Shows project/run status and start button.
- `components/DagBoard.tsx`
  - Shows tasks as stable tiles with dependency labels and output preview affordance.
- `components/HumanGatePanel.tsx`
  - Shows waiting gate prompt, reason input, approve and reject buttons.
- `components/EventTimeline.tsx`
  - Shows parsed runtime events newest or chronological order, with event type and payload summary.
- `components/AuditPanel.tsx`
  - Shows audit actions and targets.
- `components/DeliveryPanel.tsx`
  - Shows artifact, reflection, and lessons.

Keep `App.tsx` as the orchestration layer. Avoid leaving all rendering logic in `App.tsx`.

## Styling

Revise `App.css` for a restrained operations UI:

- neutral background;
- strong section boundaries;
- compact typography;
- stable tile dimensions;
- status colors used sparingly;
- 8px or smaller border radius;
- no gradient orbs, decorative blobs, or oversized hero treatment.

Use lucide icons inside buttons and section labels where helpful.

Text must not overflow buttons, task tiles, rails, or status strips on mobile or desktop.

## Backend Delivery Generation

Add a small deterministic service or private RunnerService helper that creates delivery data when a run completes.

Persist:

- `Artifact`
  - run ID;
  - generated content summarizing completed task outputs.
- `Reflection`
  - run ID;
  - generated content explaining what went well, risks, and follow-up.
- `Lesson`
  - project ID;
  - lesson content derived from the credit-card installment workflow.

Generation must be idempotent:

- repeated calls after a completed run must not duplicate artifact/reflection/lesson rows.
- existing rows should be reused.

If the current schema allows only one artifact per run, keep one artifact.

## Testing Strategy

### Backend Tests

Add or update tests proving:

- approving the HumanGate completes the run and creates artifact/reflection/lessons;
- repeated calls do not duplicate generated delivery data;
- `ProjectStateResponse` includes lessons after completion.

Run:

```powershell
cd backend-java
mvn test
```

### Frontend Tests

Use Vitest and Testing Library to prove:

- intent analysis and agent recommendation are displayed;
- project creation uses backend ProjectState;
- start run calls `/api/runs/{runId}/start`;
- HumanGate approve and reject call the correct endpoints with reason and decided_by;
- SSE events parse RuntimeEventEnvelope data and render EventTimeline;
- audit logs render actions and targets;
- delivery panel renders artifact, reflection, and lessons;
- error messages appear when API calls fail.

Run:

```powershell
cd frontend
npm test
npm run build
```

## Acceptance Criteria

Phase 5 is complete when:

- the workbench starts as the main screen;
- the frontend uses TypeScript types aligned with the current Java DTOs;
- the user can perform the golden path through the UI;
- HumanGate has both approve and reject actions;
- SSE timeline renders Phase 4 runtime event envelopes;
- audit logs render from `/api/audit-logs`;
- Artifact / Reflection / Lessons render real backend data after run completion;
- `docs/learning/phase-05-frontend-workbench.md` exists and is beginner-friendly;
- `frontend npm test` passes;
- `frontend npm run build` passes;
- `backend-java mvn test` passes.

## Risks And Mitigations

- The backend still has no full auth flow. Keep Phase 5 local-demo oriented and use `local-user` for audit and HumanGate decisions.
- The current frontend is a single large component. Split only along Phase 5 feature boundaries; avoid broad architectural refactors.
- SSE updates can race with command responses. Use command responses as state truth and timeline events as observational detail.
- Delivery generation could duplicate rows if run completion is called more than once. Make generation idempotent and cover it with tests.
