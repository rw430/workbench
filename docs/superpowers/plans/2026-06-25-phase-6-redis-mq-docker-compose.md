# Phase 6 Redis / MQ / Docker Compose Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Redis run protection, RabbitMQ-backed run queue mode, Docker Compose middleware, and beginner-friendly Phase 6 docs while keeping `LocalRunQueue` available.

**Architecture:** `RunController` continues to depend on `RunQueue`. Local mode calls `RunnerService` through `LocalRunQueue`; Rabbit mode publishes `RunStartMessage` through `RabbitRunQueue`, and `RunWorker` consumes it and calls `RunnerService`. Redis provides fixed-window rate limiting and a short run lock used by both local and Rabbit execution paths.

**Tech Stack:** Java 21, Spring Boot 3.3.6, Spring Data Redis, Spring AMQP, PostgreSQL, Redis, RabbitMQ, Testcontainers, React/Vite/Vitest, Docker Compose.

---

## File Structure

### Backend Dependencies And Configuration

- Modify: `backend-java/pom.xml`
  - Add Spring Redis and AMQP starters.
  - Add Testcontainers RabbitMQ module.
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/XiaocWorkbenchApplication.java`
  - Add `@ConfigurationPropertiesScan`.
- Modify: `backend-java/src/main/resources/application.yml`
  - Add Redis, RabbitMQ, queue, lock, and rate-limit properties.
- Modify: `backend-java/src/main/resources/application-test.yml`
  - Keep local queue as test default.
  - Disable Redis for tests that do not explicitly need Redis.
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/QueueMode.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/QueueProperties.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RedisFeatureProperties.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunLockProperties.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunStartRateLimitProperties.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueueProperties.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/common/web/RateLimitExceededException.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/common/web/InfrastructureUnavailableException.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/common/web/RunAlreadyLockedException.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/web/ApiExceptionHandler.java`

### Redis Services

- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunConcurrencyGuard.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RedisRunConcurrencyGuard.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/NoopRunConcurrencyGuard.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RateLimitService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RedisRateLimitService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/NoopRateLimitService.java`

### Queue And Worker

- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/LocalRunQueue.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunStartMessage.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueue.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueueConfig.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunWorker.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/RunController.java`

### Tests

- Modify: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/api/RunControllerTest.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/QueuePropertiesTest.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RunConcurrencyGuardTest.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RateLimitServiceTest.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueueTest.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RunWorkerTest.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/support/RedisIntegrationTest.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RedisQueueInfrastructureIntegrationTest.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueueIntegrationTest.java`

### Frontend

- Modify: `frontend/src/api.ts`
  - Add `getProject(projectId)`.
- Modify: `frontend/src/App.tsx`
  - Refresh project state when SSE announces state-changing run events.
- Modify: `frontend/src/App.test.tsx`
  - Add a Rabbit-style asynchronous start test.

### Infrastructure And Docs

- Create: `infra/docker-compose.yml`
- Modify: `infra/README.md`
- Create: `docs/runbooks/docker-compose.md`
- Create: `docs/learning/phase-06-redis-mq-docker-compose.md`
- Modify: `docs/learning/README.md` only if ordering or wording needs to mention the completed Phase 6 file.

---

## Task 1: Dependencies, Typed Properties, And API Errors

**Files:**
- Modify: `backend-java/pom.xml`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/XiaocWorkbenchApplication.java`
- Modify: `backend-java/src/main/resources/application.yml`
- Modify: `backend-java/src/main/resources/application-test.yml`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/QueueMode.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/QueueProperties.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RedisFeatureProperties.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunLockProperties.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunStartRateLimitProperties.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueueProperties.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/common/web/RateLimitExceededException.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/common/web/InfrastructureUnavailableException.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/common/web/RunAlreadyLockedException.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/web/ApiExceptionHandler.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/QueuePropertiesTest.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/api/RunControllerTest.java`

- [ ] **Step 1: Write failing property and error tests**

Create `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/QueuePropertiesTest.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "xiaoc.queue.mode=rabbit",
        "xiaoc.redis.enabled=false",
        "xiaoc.rabbitmq.enabled=false",
        "xiaoc.run-lock.ttl-seconds=45",
        "xiaoc.rate-limit.run-start.max-requests=7",
        "xiaoc.rate-limit.run-start.window-seconds=30",
        "xiaoc.rabbitmq.run-start-exchange=xiaoc.run.test",
        "xiaoc.rabbitmq.run-start-queue=xiaoc.run.start.test",
        "xiaoc.rabbitmq.run-start-routing-key=run.start.test"
})
class QueuePropertiesTest {
    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    private RedisFeatureProperties redisFeatureProperties;

    @Autowired
    private RunLockProperties runLockProperties;

    @Autowired
    private RunStartRateLimitProperties rateLimitProperties;

    @Autowired
    private RabbitRunQueueProperties rabbitProperties;

    @Test
    void bindsQueueRedisAndRabbitProperties() {
        assertThat(queueProperties.mode()).isEqualTo(QueueMode.RABBIT);
        assertThat(redisFeatureProperties.enabled()).isFalse();
        assertThat(redisFeatureProperties.failOpen()).isFalse();
        assertThat(runLockProperties.ttlSeconds()).isEqualTo(45);
        assertThat(rateLimitProperties.maxRequests()).isEqualTo(7);
        assertThat(rateLimitProperties.windowSeconds()).isEqualTo(30);
        assertThat(rabbitProperties.runStartExchange()).isEqualTo("xiaoc.run.test");
        assertThat(rabbitProperties.runStartQueue()).isEqualTo("xiaoc.run.start.test");
        assertThat(rabbitProperties.runStartRoutingKey()).isEqualTo("run.start.test");
    }
}
```

Extend `RunControllerTest` later in this task with these two tests after the exception classes exist:

```java
@Test
void mapsRateLimitFailuresToTooManyRequests() throws Exception {
    doThrow(new RateLimitExceededException("run start rate limit exceeded"))
            .when(rateLimitService).checkAllowed("local-user", "run-start");

    mockMvc.perform(post("/api/runs/run-1/start"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error").value("rate_limited"))
            .andExpect(jsonPath("$.message").value("run start rate limit exceeded"));
}

@Test
void mapsInfrastructureFailuresToServiceUnavailable() throws Exception {
    doThrow(new InfrastructureUnavailableException("redis unavailable"))
            .when(rateLimitService).checkAllowed("local-user", "run-start");

    mockMvc.perform(post("/api/runs/run-1/start"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("infrastructure_unavailable"))
            .andExpect(jsonPath("$.message").value("redis unavailable"));
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
mvn -Dtest=QueuePropertiesTest,RunControllerTest test
```

Expected:

```text
QueuePropertiesTest fails because QueueProperties and related property classes do not exist.
RunControllerTest fails after adding the new test methods because RateLimitExceededException, InfrastructureUnavailableException, and RateLimitService do not exist.
```

- [ ] **Step 3: Add Maven dependencies**

In `backend-java/pom.xml`, add these dependencies before `spring-boot-starter-test`:

```xml
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>
```

Add the RabbitMQ Testcontainers module after the PostgreSQL Testcontainers dependency:

```xml
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>rabbitmq</artifactId>
      <scope>test</scope>
    </dependency>
```

- [ ] **Step 4: Enable configuration property scanning**

Replace `backend-java/src/main/java/com/xiaoc/workbench/XiaocWorkbenchApplication.java` with:

```java
package com.xiaoc.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class XiaocWorkbenchApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaocWorkbenchApplication.class, args);
    }
}
```

- [ ] **Step 5: Add typed property classes**

Create `QueueMode.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

public enum QueueMode {
    LOCAL,
    RABBIT
}
```

Create `QueueProperties.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xiaoc.queue")
public record QueueProperties(QueueMode mode) {
    public QueueProperties {
        if (mode == null) {
            mode = QueueMode.LOCAL;
        }
    }
}
```

Create `RedisFeatureProperties.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xiaoc.redis")
public record RedisFeatureProperties(boolean enabled, boolean failOpen) {
}
```

Create `RunLockProperties.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xiaoc.run-lock")
public record RunLockProperties(long ttlSeconds) {
    public RunLockProperties {
        if (ttlSeconds <= 0) {
            ttlSeconds = 30;
        }
    }
}
```

Create `RunStartRateLimitProperties.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xiaoc.rate-limit.run-start")
public record RunStartRateLimitProperties(int maxRequests, long windowSeconds) {
    public RunStartRateLimitProperties {
        if (maxRequests <= 0) {
            maxRequests = 20;
        }
        if (windowSeconds <= 0) {
            windowSeconds = 60;
        }
    }
}
```

Create `RabbitRunQueueProperties.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xiaoc.rabbitmq")
public record RabbitRunQueueProperties(
        boolean enabled,
        String runStartExchange,
        String runStartQueue,
        String runStartRoutingKey
) {
    public RabbitRunQueueProperties {
        if (runStartExchange == null || runStartExchange.isBlank()) {
            runStartExchange = "xiaoc.run";
        }
        if (runStartQueue == null || runStartQueue.isBlank()) {
            runStartQueue = "xiaoc.run.start";
        }
        if (runStartRoutingKey == null || runStartRoutingKey.isBlank()) {
            runStartRoutingKey = "run.start";
        }
    }
}
```

- [ ] **Step 6: Add exception classes and API mappings**

Create `RateLimitExceededException.java`:

```java
package com.xiaoc.workbench.common.web;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
```

Create `InfrastructureUnavailableException.java`:

```java
package com.xiaoc.workbench.common.web;

public class InfrastructureUnavailableException extends RuntimeException {
    public InfrastructureUnavailableException(String message) {
        super(message);
    }
}
```

Create `RunAlreadyLockedException.java`:

```java
package com.xiaoc.workbench.common.web;

public class RunAlreadyLockedException extends RuntimeException {
    public RunAlreadyLockedException(String message) {
        super(message);
    }
}
```

Add these handlers to `ApiExceptionHandler` before `handleInvalidRequest`:

```java
    @ExceptionHandler(RunAlreadyLockedException.class)
    ResponseEntity<Map<String, String>> handleRunAlreadyLocked(RunAlreadyLockedException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", "run_locked", "message", exception.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<Map<String, String>> handleRateLimitExceeded(RateLimitExceededException exception) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "rate_limited", "message", exception.getMessage()));
    }

    @ExceptionHandler(InfrastructureUnavailableException.class)
    ResponseEntity<Map<String, String>> handleInfrastructureUnavailable(InfrastructureUnavailableException exception) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "infrastructure_unavailable", "message", exception.getMessage()));
    }
```

- [ ] **Step 7: Add YAML defaults**

In `application.yml`, add:

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

Merge these keys with the existing `spring:` block instead of creating a duplicate top-level `spring:` key.

In `application-test.yml`, add:

```yaml
xiaoc:
  queue:
    mode: local
  redis:
    enabled: false
    fail-open: true
```

- [ ] **Step 8: Wire rate limiting into the controller test setup**

After Task 3 creates `RateLimitService`, update `RunControllerTest` imports and fields:

```java
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.xiaoc.workbench.common.web.InfrastructureUnavailableException;
import com.xiaoc.workbench.common.web.RateLimitExceededException;
import com.xiaoc.workbench.orchestrator.queue.RateLimitService;
```

Add:

```java
    @MockBean
    private RateLimitService rateLimitService;
```

In the existing happy-path test, add:

```java
        verify(rateLimitService).checkAllowed("local-user", "run-start");
```

For the failure tests, use:

```java
doThrow(new RateLimitExceededException("run start rate limit exceeded"))
        .when(rateLimitService).checkAllowed("local-user", "run-start");
```

and:

```java
doThrow(new InfrastructureUnavailableException("redis unavailable"))
        .when(rateLimitService).checkAllowed("local-user", "run-start");
```

- [ ] **Step 9: Run focused tests**

Run:

```powershell
mvn -Dtest=QueuePropertiesTest,RunControllerTest test
```

Expected:

```text
Tests run: 4 or more, Failures: 0, Errors: 0
```

- [ ] **Step 10: Commit**

```powershell
git add backend-java/pom.xml backend-java/src/main/java backend-java/src/main/resources backend-java/src/test/java
git commit -m "feat: add phase 6 infrastructure configuration"
```

---

## Task 2: Redis Guard And Rate Limiter

**Files:**
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunConcurrencyGuard.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RedisRunConcurrencyGuard.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/NoopRunConcurrencyGuard.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RateLimitService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RedisRateLimitService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/NoopRateLimitService.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RunConcurrencyGuardTest.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RateLimitServiceTest.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/support/RedisIntegrationTest.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RedisQueueInfrastructureIntegrationTest.java`

- [ ] **Step 1: Write failing unit tests for no-op services**

Create `RunConcurrencyGuardTest.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunConcurrencyGuardTest {
    @Test
    void noopGuardRunsCallbackAndReturnsValue() {
        RunConcurrencyGuard guard = new NoopRunConcurrencyGuard();

        String result = guard.runWithLock("run-1", () -> "started");

        assertThat(result).isEqualTo("started");
    }
}
```

Create `RateLimitServiceTest.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import org.junit.jupiter.api.Test;

class RateLimitServiceTest {
    @Test
    void noopRateLimiterAllowsRequests() {
        RateLimitService service = new NoopRateLimitService();

        service.checkAllowed("local-user", "run-start");
    }
}
```

- [ ] **Step 2: Run unit tests and verify they fail**

Run:

```powershell
mvn -Dtest=RunConcurrencyGuardTest,RateLimitServiceTest test
```

Expected:

```text
Compilation fails because RunConcurrencyGuard, NoopRunConcurrencyGuard, RateLimitService, and NoopRateLimitService do not exist.
```

- [ ] **Step 3: Implement no-op interfaces**

Create `RunConcurrencyGuard.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import java.util.function.Supplier;

public interface RunConcurrencyGuard {
    <T> T runWithLock(String runId, Supplier<T> callback);
}
```

Create `NoopRunConcurrencyGuard.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.redis.enabled", havingValue = "false")
public class NoopRunConcurrencyGuard implements RunConcurrencyGuard {
    @Override
    public <T> T runWithLock(String runId, Supplier<T> callback) {
        return callback.get();
    }
}
```

Create `RateLimitService.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

public interface RateLimitService {
    void checkAllowed(String actorId, String action);
}
```

Create `NoopRateLimitService.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.redis.enabled", havingValue = "false")
public class NoopRateLimitService implements RateLimitService {
    @Override
    public void checkAllowed(String actorId, String action) {
    }
}
```

- [ ] **Step 4: Run unit tests and verify they pass**

Run:

```powershell
mvn -Dtest=RunConcurrencyGuardTest,RateLimitServiceTest test
```

Expected:

```text
Tests run: 2, Failures: 0, Errors: 0
```

- [ ] **Step 5: Write Redis integration tests**

Create `RedisIntegrationTest.java`:

```java
package com.xiaoc.workbench.support;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "xiaoc.redis.enabled=true",
        "xiaoc.redis.fail-open=false",
        "xiaoc.queue.mode=local"
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RedisIntegrationTest {
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
```

Create `RedisQueueInfrastructureIntegrationTest.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xiaoc.workbench.common.web.RateLimitExceededException;
import com.xiaoc.workbench.common.web.RunAlreadyLockedException;
import com.xiaoc.workbench.support.RedisIntegrationTest;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "xiaoc.run-lock.ttl-seconds=20",
        "xiaoc.rate-limit.run-start.max-requests=2",
        "xiaoc.rate-limit.run-start.window-seconds=60"
})
class RedisQueueInfrastructureIntegrationTest extends RedisIntegrationTest {
    @Autowired
    private RunConcurrencyGuard guard;

    @Autowired
    private RateLimitService rateLimitService;

    @Test
    void redisGuardRejectsNestedStartForSameRun() {
        assertThatThrownBy(() -> guard.runWithLock("run-lock-test", () ->
                guard.runWithLock("run-lock-test", () -> "nested")))
                .isInstanceOf(RunAlreadyLockedException.class)
                .hasMessageContaining("run-lock-test");
    }

    @Test
    void redisGuardReleasesLockAfterCallback() {
        AtomicInteger count = new AtomicInteger();

        guard.runWithLock("run-lock-release-test", () -> count.incrementAndGet());
        guard.runWithLock("run-lock-release-test", () -> count.incrementAndGet());

        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    void redisRateLimiterRejectsAfterConfiguredWindowLimit() {
        rateLimitService.checkAllowed("actor-rate-test", "run-start");
        rateLimitService.checkAllowed("actor-rate-test", "run-start");

        assertThatThrownBy(() -> rateLimitService.checkAllowed("actor-rate-test", "run-start"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("run-start");
    }
}
```

- [ ] **Step 6: Run Redis integration tests and verify they fail**

Run:

```powershell
mvn -Dtest=RedisQueueInfrastructureIntegrationTest test
```

Expected:

```text
Compilation fails because RedisRunConcurrencyGuard and RedisRateLimitService do not exist.
```

- [ ] **Step 7: Implement Redis guard**

Create `RedisRunConcurrencyGuard.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.common.web.InfrastructureUnavailableException;
import com.xiaoc.workbench.common.web.RunAlreadyLockedException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisRunConcurrencyGuard implements RunConcurrencyGuard {
    private static final String KEY_PREFIX = "xiaoc:run-lock:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RunLockProperties properties;
    private final RedisFeatureProperties redisFeatureProperties;

    public RedisRunConcurrencyGuard(
            StringRedisTemplate redisTemplate,
            RunLockProperties properties,
            RedisFeatureProperties redisFeatureProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.redisFeatureProperties = redisFeatureProperties;
    }

    @Override
    public <T> T runWithLock(String runId, Supplier<T> callback) {
        String key = KEY_PREFIX + runId;
        String ownerToken = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, ownerToken, Duration.ofSeconds(properties.ttlSeconds()));
            if (!Boolean.TRUE.equals(acquired)) {
                throw new RunAlreadyLockedException("Run is already being advanced: " + runId);
            }
            return callback.get();
        } catch (RunAlreadyLockedException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            if (redisFeatureProperties.failOpen()) {
                return callback.get();
            }
            throw new InfrastructureUnavailableException("Redis run lock unavailable: " + exception.getMessage());
        } finally {
            try {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), ownerToken);
            } catch (DataAccessException ignored) {
            }
        }
    }
}
```

- [ ] **Step 8: Implement Redis rate limiter**

Create `RedisRateLimitService.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.common.web.InfrastructureUnavailableException;
import com.xiaoc.workbench.common.web.RateLimitExceededException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisRateLimitService implements RateLimitService {
    private final StringRedisTemplate redisTemplate;
    private final RunStartRateLimitProperties properties;
    private final RedisFeatureProperties redisFeatureProperties;

    public RedisRateLimitService(
            StringRedisTemplate redisTemplate,
            RunStartRateLimitProperties properties,
            RedisFeatureProperties redisFeatureProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.redisFeatureProperties = redisFeatureProperties;
    }

    @Override
    public void checkAllowed(String actorId, String action) {
        long window = Instant.now().getEpochSecond() / properties.windowSeconds();
        String key = "xiaoc:rate-limit:" + actorId + ":" + action + ":" + window;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(properties.windowSeconds()));
            }
            if (count != null && count > properties.maxRequests()) {
                throw new RateLimitExceededException(action + " rate limit exceeded for " + actorId);
            }
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            if (redisFeatureProperties.failOpen()) {
                return;
            }
            throw new InfrastructureUnavailableException("Redis rate limit unavailable: " + exception.getMessage());
        }
    }
}
```

- [ ] **Step 9: Run Redis tests**

Run:

```powershell
mvn -Dtest=RunConcurrencyGuardTest,RateLimitServiceTest,RedisQueueInfrastructureIntegrationTest test
```

Expected:

```text
All selected tests pass.
```

- [ ] **Step 10: Commit**

```powershell
git add backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue backend-java/src/test/java/com/xiaoc/workbench
git commit -m "feat: add redis run guard and rate limiter"
```

---

## Task 3: Wire Local Queue And Run Start Rate Limiting

**Files:**
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/api/RunController.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/LocalRunQueue.java`
- Modify: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/api/RunControllerTest.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/LocalRunQueueTest.java`

- [ ] **Step 1: Write failing LocalRunQueue test**

Create `LocalRunQueueTest.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiaoc.workbench.orchestrator.service.RunnerService;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LocalRunQueueTest {
    @Test
    void runsRunnerInsideConcurrencyGuard() {
        RunnerService runnerService = Mockito.mock(RunnerService.class);
        RunConcurrencyGuard guard = Mockito.mock(RunConcurrencyGuard.class);
        ProjectStateResponse response = Mockito.mock(ProjectStateResponse.class);
        when(guard.runWithLock(eq("run-1"), any())).thenReturn(response);

        LocalRunQueue queue = new LocalRunQueue(runnerService, guard);

        assertThat(queue.enqueueStart("run-1")).isSameAs(response);
        verify(guard).runWithLock(eq("run-1"), any());
    }
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run:

```powershell
mvn -Dtest=LocalRunQueueTest,RunControllerTest test
```

Expected:

```text
LocalRunQueueTest fails because the LocalRunQueue constructor does not accept RunConcurrencyGuard.
RunControllerTest fails until RunController injects RateLimitService.
```

- [ ] **Step 3: Update LocalRunQueue**

Replace `LocalRunQueue.java` with:

```java
package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.orchestrator.service.RunnerService;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.queue.mode", havingValue = "local", matchIfMissing = true)
public class LocalRunQueue implements RunQueue {
    private final RunnerService runnerService;
    private final RunConcurrencyGuard runConcurrencyGuard;

    public LocalRunQueue(RunnerService runnerService, RunConcurrencyGuard runConcurrencyGuard) {
        this.runnerService = runnerService;
        this.runConcurrencyGuard = runConcurrencyGuard;
    }

    @Override
    public ProjectStateResponse enqueueStart(String runId) {
        return runConcurrencyGuard.runWithLock(runId, () -> runnerService.startRun(runId));
    }
}
```

- [ ] **Step 4: Update RunController**

Replace `RunController.java` with:

```java
package com.xiaoc.workbench.orchestrator.api;

import com.xiaoc.workbench.orchestrator.queue.RateLimitService;
import com.xiaoc.workbench.orchestrator.queue.RunQueue;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public class RunController {
    private static final String LOCAL_ACTOR = "local-user";
    private static final String RUN_START_ACTION = "run-start";

    private final RunQueue runQueue;
    private final RateLimitService rateLimitService;

    public RunController(RunQueue runQueue, RateLimitService rateLimitService) {
        this.runQueue = runQueue;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/{runId}/start")
    public ProjectStateResponse startRun(@PathVariable String runId) {
        rateLimitService.checkAllowed(LOCAL_ACTOR, RUN_START_ACTION);
        return runQueue.enqueueStart(runId);
    }
}
```

- [ ] **Step 5: Run focused tests**

Run:

```powershell
mvn -Dtest=LocalRunQueueTest,RunControllerTest test
```

Expected:

```text
All selected tests pass.
```

- [ ] **Step 6: Run existing runner tests**

Run:

```powershell
mvn -Dtest=RunnerServiceTest,RunnerDeliveryIntegrationTest,RunnerEventAuditIntegrationTest test
```

Expected:

```text
Existing local runner behavior remains green.
```

- [ ] **Step 7: Commit**

```powershell
git add backend-java/src/main/java/com/xiaoc/workbench/orchestrator backend-java/src/test/java/com/xiaoc/workbench/orchestrator
git commit -m "feat: protect local run starts"
```

---

## Task 4: RabbitMQ Producer, Worker, And Queue Mode

**Files:**
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunStartMessage.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueueConfig.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueue.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue/RunWorker.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueueTest.java`
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RunWorkerTest.java`

- [ ] **Step 1: Write RabbitRunQueue unit test**

Create `RabbitRunQueueTest.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.service.ProjectApplicationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitRunQueueTest {
    @Test
    void publishesRunStartMessageAndReturnsCurrentState() {
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        ProjectApplicationService projectService = Mockito.mock(ProjectApplicationService.class);
        RabbitRunQueueProperties properties = new RabbitRunQueueProperties(
                true,
                "xiaoc.run",
                "xiaoc.run.start",
                "run.start");
        ProjectStateResponse state = Mockito.mock(ProjectStateResponse.class);
        when(projectService.getRunState("run-1")).thenReturn(state);
        RabbitRunQueue queue = new RabbitRunQueue(rabbitTemplate, projectService, properties);

        ProjectStateResponse returned = queue.enqueueStart("run-1");

        ArgumentCaptor<RunStartMessage> message = ArgumentCaptor.forClass(RunStartMessage.class);
        verify(rabbitTemplate).convertAndSend(eq("xiaoc.run"), eq("run.start"), message.capture());
        assertThat(message.getValue().runId()).isEqualTo("run-1");
        assertThat(message.getValue().requestedBy()).isEqualTo("local-user");
        assertThat(message.getValue().traceId()).startsWith("run-start-");
        assertThat(returned).isSameAs(state);
    }
}
```

- [ ] **Step 2: Write RunWorker unit test**

Create `RunWorkerTest.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiaoc.workbench.orchestrator.service.RunnerService;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RunWorkerTest {
    @Test
    void consumesMessageThroughConcurrencyGuard() {
        RunnerService runnerService = Mockito.mock(RunnerService.class);
        RunConcurrencyGuard guard = Mockito.mock(RunConcurrencyGuard.class);
        RunWorker worker = new RunWorker(runnerService, guard);
        RunStartMessage message = new RunStartMessage(
                "run-1",
                "local-user",
                Instant.parse("2026-06-25T00:00:00Z"),
                "trace-1");

        when(guard.runWithLock(eq("run-1"), any())).thenAnswer(invocation -> {
            Supplier<?> callback = invocation.getArgument(1);
            callback.get();
            return null;
        });

        worker.handle(message);

        verify(guard).runWithLock(eq("run-1"), any());
        verify(runnerService).startRun("run-1");
    }
}
```

- [ ] **Step 3: Run Rabbit unit tests and verify failure**

Run:

```powershell
mvn -Dtest=RabbitRunQueueTest,RunWorkerTest test
```

Expected:

```text
Compilation fails because RunStartMessage, RabbitRunQueue, and RunWorker do not exist.
```

- [ ] **Step 4: Add RunStartMessage**

Create `RunStartMessage.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import java.time.Instant;

public record RunStartMessage(
        String runId,
        String requestedBy,
        Instant requestedAt,
        String traceId
) {
}
```

- [ ] **Step 5: Add RabbitMQ configuration**

Create `RabbitRunQueueConfig.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "xiaoc.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitRunQueueConfig {
    @Bean
    TopicExchange runStartExchange(RabbitRunQueueProperties properties) {
        return new TopicExchange(properties.runStartExchange(), true, false);
    }

    @Bean
    Queue runStartQueue(RabbitRunQueueProperties properties) {
        return new Queue(properties.runStartQueue(), true);
    }

    @Bean
    Binding runStartBinding(TopicExchange runStartExchange, Queue runStartQueue, RabbitRunQueueProperties properties) {
        return BindingBuilder.bind(runStartQueue)
                .to(runStartExchange)
                .with(properties.runStartRoutingKey());
    }

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
```

- [ ] **Step 6: Add RabbitRunQueue**

Create `RabbitRunQueue.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.service.ProjectApplicationService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.queue.mode", havingValue = "rabbit")
public class RabbitRunQueue implements RunQueue {
    private final RabbitTemplate rabbitTemplate;
    private final ProjectApplicationService projectApplicationService;
    private final RabbitRunQueueProperties properties;

    public RabbitRunQueue(
            RabbitTemplate rabbitTemplate,
            ProjectApplicationService projectApplicationService,
            RabbitRunQueueProperties properties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.projectApplicationService = projectApplicationService;
        this.properties = properties;
    }

    @Override
    public ProjectStateResponse enqueueStart(String runId) {
        RunStartMessage message = new RunStartMessage(
                runId,
                "local-user",
                Instant.now(),
                "run-start-" + UUID.randomUUID());
        try {
            rabbitTemplate.convertAndSend(
                    properties.runStartExchange(),
                    properties.runStartRoutingKey(),
                    message);
        } catch (AmqpException exception) {
            throw new com.xiaoc.workbench.common.web.InfrastructureUnavailableException(
                    "RabbitMQ run queue unavailable: " + exception.getMessage());
        }
        return projectApplicationService.getRunState(runId);
    }
}
```

- [ ] **Step 7: Add RunWorker**

Create `RunWorker.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.common.web.RunAlreadyLockedException;
import com.xiaoc.workbench.orchestrator.service.RunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.queue.mode", havingValue = "rabbit")
public class RunWorker {
    private static final Logger log = LoggerFactory.getLogger(RunWorker.class);

    private final RunnerService runnerService;
    private final RunConcurrencyGuard runConcurrencyGuard;

    public RunWorker(RunnerService runnerService, RunConcurrencyGuard runConcurrencyGuard) {
        this.runnerService = runnerService;
        this.runConcurrencyGuard = runConcurrencyGuard;
    }

    @RabbitListener(queues = "${xiaoc.rabbitmq.run-start-queue}")
    public void handle(RunStartMessage message) {
        try {
            runConcurrencyGuard.runWithLock(message.runId(), () -> {
                runnerService.startRun(message.runId());
                return null;
            });
        } catch (RunAlreadyLockedException exception) {
            log.info("Skipping locked run {}", message.runId());
        }
    }
}
```

- [ ] **Step 8: Run Rabbit unit tests**

Run:

```powershell
mvn -Dtest=RabbitRunQueueTest,RunWorkerTest test
```

Expected:

```text
Tests run: 2, Failures: 0, Errors: 0
```

- [ ] **Step 9: Commit**

```powershell
git add backend-java/src/main/java/com/xiaoc/workbench/orchestrator/queue backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue
git commit -m "feat: add rabbit run queue worker"
```

---

## Task 5: RabbitMQ End-To-End Integration

**Files:**
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueueIntegrationTest.java`

- [ ] **Step 1: Write Rabbit mode integration test**

Create `RabbitRunQueueIntegrationTest.java`:

```java
package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;


import com.xiaoc.workbench.agent.service.AgentRecommendationService;
import com.xiaoc.workbench.agent.service.BuiltinAgentSeeder;
import com.xiaoc.workbench.event.service.RuntimeEventService;
import com.xiaoc.workbench.governance.service.AuditLogService;
import com.xiaoc.workbench.growth.service.DeliveryGenerationService;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import com.xiaoc.workbench.orchestrator.service.DeterministicTaskExecutor;
import com.xiaoc.workbench.orchestrator.service.RunnerService;
import com.xiaoc.workbench.orchestrator.template.DagTemplateLoader;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.service.ProjectApplicationService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "xiaoc.queue.mode=rabbit",
        "xiaoc.redis.enabled=true",
        "xiaoc.redis.fail-open=false",
        "xiaoc.rabbitmq.enabled=true",
        "xiaoc.run-lock.ttl-seconds=20",
        "xiaoc.rate-limit.run-start.max-requests=20",
        "xiaoc.rate-limit.run-start.window-seconds=60"
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({
        BuiltinAgentSeeder.class,
        IntentAnalysisService.class,
        AgentRecommendationService.class,
        DagTemplateLoader.class,
        RuntimeEventService.class,
        AuditLogService.class,
        DeliveryGenerationService.class,
        ProjectApplicationService.class,
        DeterministicTaskExecutor.class,
        RunnerService.class
})
class RabbitRunQueueIntegrationTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("xiaoc_test")
            .withUsername("xiaoc")
            .withPassword("xiaoc");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withUser("xiaoc", "xiaoc")
            .withPermission("/", "xiaoc", ".*", ".*", ".*");

    static {
        POSTGRES.start();
        REDIS.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "xiaoc");
        registry.add("spring.rabbitmq.password", () -> "xiaoc");
    }

    @Autowired
    private BuiltinAgentSeeder seeder;

    @Autowired
    private ProjectApplicationService projectService;

    @Autowired
    private RunQueue runQueue;

    @Test
    void rabbitQueuePublishesAndWorkerAdvancesRun() throws Exception {
        seeder.seedBuiltinAgents();
        ProjectStateResponse created = projectService.createProject("credit card installment campaign with approval");

        ProjectStateResponse immediate = runQueue.enqueueStart(created.run().id());

        assertThat(immediate.run().id()).isEqualTo(created.run().id());
        ProjectStateResponse eventual = waitForRunStatus(created.run().id(), "waiting_human", Duration.ofSeconds(20));
        assertThat(eventual.humanGate()).isNotNull();
        assertThat(eventual.tasks()).anySatisfy(task -> {
            assertThat(task.nodeId()).isEqualTo("need_analysis");
            assertThat(task.status()).isEqualTo("completed");
        });
    }

    private ProjectStateResponse waitForRunStatus(String runId, String status, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        ProjectStateResponse state = projectService.getRunState(runId);
        while (System.nanoTime() < deadline) {
            state = projectService.getRunState(runId);
            if (status.equals(state.run().status())) {
                return state;
            }
            Thread.sleep(250);
        }
        return state;
    }

}
```

- [ ] **Step 2: Run the Rabbit integration test**

Run:

```powershell
mvn -Dtest=RabbitRunQueueIntegrationTest test
```

Expected:

```text
Test starts PostgreSQL, Redis, and RabbitMQ containers.
The run reaches waiting_human through the Rabbit worker.
```

- [ ] **Step 3: Fix integration wiring if needed**

If the listener does not deserialize `RunStartMessage`, check:

```text
RabbitRunQueueConfig exposes Jackson2JsonMessageConverter.
RunStartMessage is a public record.
rabbitListenerContainerFactory uses the same converter.
```

If the listener never starts, check:

```text
xiaoc.queue.mode=rabbit is present in the test properties.
@RabbitListener queue name matches xiaoc.rabbitmq.run-start-queue.
RabbitRunQueueConfig declares the queue binding.
```

- [ ] **Step 4: Commit**

```powershell
git add backend-java/src/test/java/com/xiaoc/workbench/orchestrator/queue/RabbitRunQueueIntegrationTest.java
git commit -m "test: verify rabbit run worker integration"
```

---

## Task 6: Frontend Async Run Refresh

**Files:**
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: Write failing frontend test for Rabbit-style async start**

Add this test to `frontend/src/App.test.tsx`:

```tsx
it("refreshes project state when asynchronous run events arrive", async () => {
  installFakeEventSource();
  vi.spyOn(api, "analyzeIntent").mockResolvedValue(intent);
  vi.spyOn(api, "recommendAgents").mockResolvedValue(agents);
  vi.spyOn(api, "createProject").mockResolvedValue({
    ...waitingProjectState,
    project: { ...waitingProjectState.project, status: "created" },
    run: { ...waitingProjectState.run, status: "created" },
    human_gate: null,
    tasks: waitingProjectState.tasks.map((task) => ({ ...task, status: "pending" })),
  });
  vi.spyOn(api, "startRun").mockResolvedValue({
    ...waitingProjectState,
    project: { ...waitingProjectState.project, status: "created" },
    run: { ...waitingProjectState.run, status: "created" },
    human_gate: null,
    tasks: waitingProjectState.tasks.map((task) => ({ ...task, status: "pending" })),
  });
  vi.spyOn(api, "getProject").mockResolvedValue(waitingProjectState);
  vi.spyOn(api, "listAuditLogs").mockResolvedValue([]);

  render(<App />);
  await userEvent.click(screen.getByRole("button", { name: /Analyze/i }));
  await userEvent.click(await screen.findByRole("button", { name: /Create Project/i }));
  await userEvent.click(screen.getByRole("button", { name: /Start Run/i }));

  FakeEventSource.instances[0].emit(
    "human_gate.waiting",
    JSON.stringify({
      id: "event-rabbit-1",
      run_id: "run-1",
      event_type: "human_gate.waiting",
      payload: { gate_id: "gate-1" },
      created_at: "2026-06-25T00:00:00Z",
    }),
  );

  expect(await screen.findByText("Confirm PRD scope before risk review.")).toBeInTheDocument();
  expect(api.getProject).toHaveBeenCalledWith("project-1");
});
```

- [ ] **Step 2: Run frontend test and verify failure**

Run:

```powershell
npm test -- --runInBand
```

If Vitest rejects `--runInBand`, run:

```powershell
npm test
```

Expected:

```text
Test fails because api.getProject is not exported or App does not refresh state on SSE.
```

- [ ] **Step 3: Add getProject API function**

In `frontend/src/api.ts`, add:

```ts
export async function getProject(projectId: string): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/projects/${projectId}`);
  return parseJsonResponse<ProjectState>(response, "Get project");
}
```

- [ ] **Step 4: Refresh state after state-changing SSE events**

In `frontend/src/App.tsx`, import `getProject`:

```ts
  getProject,
```

Inside `handleEvent`, after adding the timeline item, add:

```ts
      if (
        projectState?.project.id &&
        [
          "run.started",
          "task.completed",
          "human_gate.waiting",
          "human_gate.approved",
          "human_gate.rejected",
          "run.completed",
          "run.failed",
        ].includes(envelope.event_type)
      ) {
        void getProject(projectState.project.id)
          .then(setProjectState)
          .catch((err) => {
            setError(err instanceof Error ? err.message : "Refresh failed");
          });
      }
```

Because `handleEvent` closes over `projectState?.project.id`, include `projectState?.project.id` in the SSE `useEffect` dependency list:

```ts
  }, [projectState?.run.id, projectState?.project.id]);
```

- [ ] **Step 5: Run frontend tests**

Run:

```powershell
npm test
```

Expected:

```text
1 test file passes with 4 tests.
```

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/api.ts frontend/src/App.tsx frontend/src/App.test.tsx
git commit -m "feat: refresh workbench after async run events"
```

---

## Task 7: Docker Compose And Runbook

**Files:**
- Create: `infra/docker-compose.yml`
- Modify: `infra/README.md`
- Create: `docs/runbooks/docker-compose.md`

- [ ] **Step 1: Create Docker Compose file**

Create `infra/docker-compose.yml`:

```yaml
name: xiaoc-workbench

services:
  postgres:
    image: postgres:16-alpine
    container_name: xiaoc-postgres
    environment:
      POSTGRES_DB: xiaoc_workbench
      POSTGRES_USER: xiaoc
      POSTGRES_PASSWORD: xiaoc
    ports:
      - "5432:5432"
    volumes:
      - xiaoc-postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U xiaoc -d xiaoc_workbench"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: xiaoc-redis
    command: ["redis-server", "--appendonly", "yes"]
    ports:
      - "6379:6379"
    volumes:
      - xiaoc-redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    container_name: xiaoc-rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: xiaoc
      RABBITMQ_DEFAULT_PASS: xiaoc
    ports:
      - "5672:5672"
      - "15672:15672"
    volumes:
      - xiaoc-rabbitmq-data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  xiaoc-postgres-data:
  xiaoc-redis-data:
  xiaoc-rabbitmq-data:
```

- [ ] **Step 2: Update infra README**

Replace `infra/README.md` with:

```markdown
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
```

- [ ] **Step 3: Add runbook**

Create `docs/runbooks/docker-compose.md` with:

```markdown
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
```

- [ ] **Step 4: Validate Compose config**

Run:

```powershell
docker compose -f infra/docker-compose.yml config
```

Expected:

```text
Docker Compose prints normalized service configuration and exits 0.
```

- [ ] **Step 5: Commit**

```powershell
git add infra/README.md infra/docker-compose.yml docs/runbooks/docker-compose.md
git commit -m "chore: add local middleware compose stack"
```

---

## Task 8: Beginner Learning Document

**Files:**
- Create: `docs/learning/phase-06-redis-mq-docker-compose.md`

- [ ] **Step 1: Write the learning document**

Create `docs/learning/phase-06-redis-mq-docker-compose.md` with these sections:

```markdown
# Phase 06: Redis / MQ / Docker Compose

## 怎么使用这份文档

这份文档假设你刚开始学习后端工程。先按顺序读概念，再照着 runbook 启动服务，最后回到代码里看每个类负责什么。

## 学习目标

读完后你应该能解释 Docker Compose、Redis、RabbitMQ、run 锁、限流、生产者、消费者、队列模式和本地 fallback。

## 业务背景

Phase 5 已经可以从前端启动一个 run。Phase 6 解决的是工程化问题：怎样让本地环境更接近真实后端，怎样避免重复启动，怎样把长任务交给消息队列。

## 先建立最小概念

Docker 是用来运行隔离服务的工具。Docker Compose 是用一个 YAML 文件同时启动多个服务的工具。

Redis 是内存型数据存储，适合短期状态，例如锁、计数器和缓存。

RabbitMQ 是消息队列，适合把“我要启动某个 run”这种命令交给后台 worker 慢慢处理。

## 核心概念

### Redis run 锁

同一个 run 不能被两个 worker 同时推进。锁的 key 是 `xiaoc:run-lock:{runId}`，TTL 防止进程崩溃后永远锁住。

### Redis 限流

限流用固定窗口计数。用户每次点击 Start Run 都会增加计数，超过阈值后返回 429。

### RabbitMQ 生产者和消费者

`RabbitRunQueue` 是生产者，负责发布 `RunStartMessage`。`RunWorker` 是消费者，收到消息后调用 `RunnerService`。

### LocalRunQueue 为什么还在

本地队列方便测试和讲解。RabbitMQ 模式更接近生产，但也带来异步时序、broker 可用性和消息重复投递问题。

## 设计原因

Controller 只依赖 `RunQueue`，所以它不需要知道底层是本地调用还是 RabbitMQ。Redis 锁放在真正执行 Runner 前面，因此 local 和 rabbit 两种模式都能复用。

## 端到端流程

Local 模式：

```text
前端 -> RunController -> LocalRunQueue -> Redis lock -> RunnerService
```

Rabbit 模式：

```text
前端 -> RunController -> RabbitRunQueue -> RabbitMQ -> RunWorker -> Redis lock -> RunnerService
```

## 代码导读

讲解 `RunQueue`、`LocalRunQueue`、`RabbitRunQueue`、`RunWorker`、`RunConcurrencyGuard`、`RateLimitService`、`infra/docker-compose.yml`、`docs/runbooks/docker-compose.md`。

## 边界条件

重复点击 Start Run 会被限流或锁保护。Redis 不可用时返回明确错误。RabbitMQ 不可用时，rabbit 模式不能假装入队成功。

## 测试说明

单元测试验证接口边界。Redis 集成测试用 Testcontainers Redis。RabbitMQ 集成测试用 Testcontainers RabbitMQ。Compose 配置用 `docker compose config` 验证。

## 排错手册

Docker daemon 连接失败时启动 Docker Desktop。端口冲突时查占用端口。Redis 失败时检查 `xiaoc-redis`。RabbitMQ 登录失败时使用 `xiaoc` / `xiaoc`。

## 面试讲法

可以说：我先用 `RunQueue` 保持 Controller 稳定，再提供 local 和 rabbit 两种实现。Redis 用于短锁和限流，RabbitMQ 用于后台任务分发，Docker Compose 用于本地复现完整中间件环境。

## 学习检查清单

- 我能画出 local 和 rabbit 两条 run 启动链路。
- 我能解释 Redis 锁为什么必须有 TTL。
- 我能解释为什么 RabbitMQ 会改变 API 的时间语义。
- 我能用 Docker Compose 启动 PostgreSQL、Redis 和 RabbitMQ。
```

Write every section as a beginner lesson: start with the problem it solves, define each new noun before using it, show one concrete command or code example when a concept is operational, and end with one common mistake plus how to recognize it. The final document must include enough explanation for a reader who has never used Redis, RabbitMQ, Docker Compose, or Spring profiles before, and the verification scan below must return no matches.

- [ ] **Step 2: Scan for incomplete markers**

Run:

```powershell
Select-String -Path docs\learning\phase-06-redis-mq-docker-compose.md -Pattern 'T[B]D','TO[D]O','late[r] fill','placeholde[r]'
```

Expected:

```text
No matches.
```

- [ ] **Step 3: Commit**

```powershell
git add docs/learning/phase-06-redis-mq-docker-compose.md
git commit -m "docs: add phase 6 learning guide"
```

---

## Task 9: Full Verification And Completion Review

**Files:**
- All files changed in Tasks 1-8.

- [ ] **Step 1: Run backend test suite**

Run:

```powershell
cd backend-java
mvn test
```

Expected:

```text
BUILD SUCCESS
Tests run includes existing 52 tests plus Phase 6 tests.
Failures: 0
Errors: 0
```

- [ ] **Step 2: Run frontend tests**

Run:

```powershell
cd frontend
npm test
```

Expected:

```text
1 test file passed
4 tests passed
```

- [ ] **Step 3: Run frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected:

```text
tsc && vite build completes successfully.
```

- [ ] **Step 4: Validate Docker Compose**

Run:

```powershell
docker compose -f infra/docker-compose.yml config
```

Expected:

```text
Compose renders normalized configuration and exits 0.
```

- [ ] **Step 5: Check whitespace**

Run:

```powershell
git diff --check
```

Expected:

```text
No output and exit 0.
```

- [ ] **Step 6: Inspect acceptance criteria**

Verify each item from the spec:

```text
1. infra/docker-compose.yml contains postgres, redis, rabbitmq.
2. application.yml contains spring.data.redis, spring.rabbitmq, xiaoc.queue, xiaoc.run-lock, xiaoc.rate-limit, xiaoc.rabbitmq.
3. LocalRunQueue still exists and is conditional on queue mode local.
4. RabbitRunQueue and RunWorker exist and are tested.
5. RedisRunConcurrencyGuard exists and is tested.
6. RedisRateLimitService exists and is tested.
7. docs/runbooks/docker-compose.md exists.
8. docs/learning/phase-06-redis-mq-docker-compose.md exists and has no incomplete markers.
9. Verification commands above pass.
```

- [ ] **Step 7: Commit any final fixes**

If verification required edits, commit them:

```powershell
git add .
git commit -m "test: complete phase 6 verification"
```

Skip this commit only if `git status --short` is clean.
