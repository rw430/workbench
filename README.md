# Xiaoc Java AgentOps Workbench

This repository is being reorganized around a Java-first AI AgentOps workbench.

The product goal is a complete learning and interview project: a Java/Spring Boot backend, React frontend, PostgreSQL/Flyway persistence, Redis and MQ infrastructure, SSE event replay, audit logging, and module-by-module learning documents.

## Golden Scenario

The first complete demo path is:

```text
Bank credit-card installment campaign configuration and approval system R&D collaboration
```

The workbench is not the credit-card production system itself. It is an AI AgentOps workbench that takes this banking R&D requirement and drives:

```text
intent analysis -> agent recommendation -> project/run/task DAG creation
-> runner execution -> HumanGate approval -> delivery artifact
-> REFLECTION -> Lessons
```

## Repository Layout

```text
backend-java/        Java 21 + Spring Boot backend
frontend/            React + TypeScript workbench UI
templates/           DAG templates
infra/               Docker Compose and middleware config
scripts/             Local verification and maintenance scripts
docs/
  product/           Product scope and banking golden scenario
  architecture/      Architecture, data flow, module boundaries
  learning/          Deep module learning notes
  adr/               Architecture decision records
  api/               API contracts
  runbooks/          Startup, deployment, troubleshooting, demo scripts
  archive/           Original reference documents
```

## Current State

The Java backend foundation is available under `backend-java/`:

- Spring Boot 3.3.x, Java 21, Maven
- PostgreSQL + Flyway migrations
- JPA entities and repositories for auth, agent, project, OMA, growth, and audit
- Testcontainers integration tests

The next implementation phase should build the Java golden path: intent analysis, agent recommendation, project creation, DAG instantiation, async runner, HumanGate approve/reject, SSE replay, and frontend integration.

## Verification

```powershell
cd backend-java
mvn test
```

Frontend verification after installing dependencies:

```powershell
cd frontend
npm install
npm test
npm run build
```

