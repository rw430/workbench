# Infrastructure

This directory contains local middleware configuration for the Xiaoc workbench.

## Services

- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`
- RabbitMQ AMQP on `localhost:5672`
- RabbitMQ Management UI on `http://localhost:15672`

## Start

```powershell
docker compose -f infra/docker-compose.yml up -d
```

## Validate

```powershell
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml ps
```

## Stop

```powershell
docker compose -f infra/docker-compose.yml down
```

