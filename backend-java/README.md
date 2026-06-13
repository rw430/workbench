# Xiaoc Workbench Java Backend

This service is the Java/Spring Boot backend for the AI AgentOps workbench.

## Requirements

- JDK 21
- Maven 3.9+
- Docker Desktop for Testcontainers and later PostgreSQL/Redis compose runs

## Verify Toolchain

```powershell
java -version
mvn -version
docker --version
```

`java -version` and `mvn -version` must both report Java 21.

## Run Tests

```powershell
mvn test
```

Repository tests use Testcontainers with PostgreSQL 16.

## Run Locally

Starting the Java backend outside tests requires a reachable PostgreSQL database.
By default the app uses:

```text
XIAOC_DB_URL=jdbc:postgresql://localhost:5432/xiaoc_workbench
XIAOC_DB_USER=xiaoc
XIAOC_DB_PASSWORD=xiaoc
```

`mvn test` does not require that local database because repository tests start PostgreSQL through Testcontainers.

## Local Service Port

The Java backend uses port `8889`.

## Phase 1 Scope

Phase 1 provides:

- Spring Boot 3 service scaffold
- Health endpoint
- Flyway migrations
- Auth, Agent, Project, OMA, Growth, and Audit persistence foundations
- Built-in PD/DEV/QA/RISK/PMO Agent seed data
