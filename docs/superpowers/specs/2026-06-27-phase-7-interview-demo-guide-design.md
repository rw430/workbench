# Phase 7 Interview Demo Guide Design

## Goal

Phase 7 turns the completed Xiaoc workbench into interview-ready material: a repeatable live demo, a project narrative, and answers to likely technical follow-up questions.

## Audience

The primary reader is the project owner preparing for autumn recruiting interviews. The reader may not yet be comfortable explaining every backend concept, so the guide must teach what to say, why it matters, and which evidence in the project supports each claim.

## Scope

Create documentation only. This phase does not add backend APIs, frontend UI, database migrations, or new runtime behavior.

Deliverables:

- `docs/learning/phase-07-interview-demo-guide.md`
  - A teaching-style guide for explaining the project in interviews.
  - Includes short pitch, architecture story, phase-by-phase explanation, technical highlights, common questions, and recovery tactics when a demo fails.
- `docs/runbooks/interview-demo.md`
  - A practical runbook for local demo setup, local queue demo, rabbit queue demo, and shutdown.
  - Includes commands, expected observations, and fallback path.
- `docs/runbooks/README.md`
  - Update the runbook index to include the new interview demo runbook.

## Non-Goals

- Do not create a polished slide deck.
- Do not generate resume text in multiple variants.
- Do not change application behavior.
- Do not require cloud deployment.
- Do not depend on remote services beyond the existing GitHub repository and local Docker images.

## Demo Story

The interview demo should tell one coherent story:

1. The product simulates an AgentOps-style workbench for credit-card installment campaign delivery.
2. The user enters a business goal.
3. The backend analyzes intent and recommends agents.
4. The system creates a project, room, run, DAG tasks, and edges.
5. The runner advances tasks, stops at a HumanGate, and records runtime events.
6. The user approves the gate.
7. The run completes with artifact, reflection, lessons, audit logs, and event timeline.
8. Phase 6 extends the local system with Redis locks, Redis rate limiting, RabbitMQ queue mode, and Docker Compose middleware.

The live demo should default to local queue mode because it is more stable under interview time pressure. Rabbit mode should be shown as an optional engineering-depth segment.

## Explanation Model

The guide should teach the user to explain the project in three layers:

- Product layer: what problem the workbench solves.
- Architecture layer: how frontend, backend, database, SSE, Redis, RabbitMQ, and Docker Compose cooperate.
- Engineering layer: how the implementation handles state, events, human approval, concurrency, async execution, tests, and local reproducibility.

## Acceptance Criteria

- The learning guide exists and is written in beginner-friendly Chinese.
- The demo runbook exists and can be followed from a fresh terminal.
- The runbook includes both local queue and rabbit queue variants.
- The documentation includes concise interview answers for Spring Boot, React/TypeScript, SSE, Redis lock, rate limiting, RabbitMQ, Docker Compose, testing, and tradeoffs.
- The documentation includes a fallback plan when RabbitMQ or Docker is not ready.
- No incomplete markers remain in the new files.
- Documentation changes are committed on the Phase 7 branch.
