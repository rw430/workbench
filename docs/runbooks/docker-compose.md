# Docker Compose Runbook

## Purpose

Use this runbook to start the local middleware needed by Phase 6.

## Start Middleware

From the repository root:

```powershell
docker compose -f infra/docker-compose.yml up -d
```

## Check Services

```powershell
docker compose -f infra/docker-compose.yml ps
```

Expected ports:

- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- RabbitMQ: `localhost:5672`
- RabbitMQ Management: `http://localhost:15672`

RabbitMQ login:

```text
username: xiaoc
password: xiaoc
```

## Start Backend In Local Queue Mode

```powershell
cd backend-java
mvn spring-boot:run
```

## Start Backend In Rabbit Queue Mode

```powershell
$env:XIAOC_QUEUE_MODE="rabbit"
cd backend-java
mvn spring-boot:run
```

## Start Frontend

```powershell
cd frontend
npm run dev -- --host 127.0.0.1 --port 5173
```

## Stop Middleware

```powershell
docker compose -f infra/docker-compose.yml down
```

## Reset Middleware Data

```powershell
docker compose -f infra/docker-compose.yml down -v
```

This deletes local PostgreSQL, Redis, and RabbitMQ volumes for this project.

## Troubleshooting

If Docker commands fail with daemon connection errors, start Docker Desktop and rerun `docker info`.

If port `5432`, `6379`, `5672`, or `15672` is already used, stop the conflicting local service or change the host port in `infra/docker-compose.yml`.

If Redis connection fails while the backend starts, verify `docker compose -f infra/docker-compose.yml ps` shows `xiaoc-redis` as running.

If RabbitMQ login fails, use `xiaoc` / `xiaoc` and wait until the RabbitMQ healthcheck is healthy.
