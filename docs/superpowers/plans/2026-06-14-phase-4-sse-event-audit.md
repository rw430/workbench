# Phase 4 SSE Event Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Phase 4 backend observability: runtime event JSON envelopes, SSE replay, audit logs for key operations, and a beginner-friendly learning guide.

**Architecture:** Runner and project services call small event and audit services. `RuntimeEventService` persists JSON payloads and replays envelopes from PostgreSQL; `RuntimeEventStreamService` owns active `SseEmitter` clients. Audit logging is separate from runtime events because audit answers "who did what" while runtime events answer "what happened in a run."

**Tech Stack:** Java 21, Spring Boot 3.3.6, Spring MVC `SseEmitter`, Jackson, Spring Data JPA, PostgreSQL/Flyway, JUnit 5, AssertJ, MockMvc.

---

## File Structure

- Create: `backend-java/src/main/java/com/xiaoc/workbench/event/api/RuntimeEventEnvelope.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/event/api/EventController.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/event/service/RuntimeEventService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/event/service/RuntimeEventStreamService.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/repository/RuntimeEventRepository.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/governance/api/AuditLogSummary.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/governance/api/AuditLogController.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/governance/service/AuditLogService.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/governance/repository/AuditLogRepository.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/RunnerService.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/event/service/RuntimeEventServiceTest.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/event/api/EventControllerTest.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/governance/api/AuditLogControllerTest.java`
- Test: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/service/RunnerEventAuditIntegrationTest.java`
- Create: `docs/learning/phase-04-sse-event-audit.md`

---

### Task 1: Runtime Event Service And Replay

**Files:**
- Create: `backend-java/src/test/java/com/xiaoc/workbench/event/service/RuntimeEventServiceTest.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/event/api/RuntimeEventEnvelope.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/event/service/RuntimeEventService.java`

- [ ] **Step 1: Write failing runtime event service tests**

Test behavior:

```java
@Test
void appendsJsonPayloadAndReplaysEventsAfterId() {
    RuntimeEventEnvelope first = service.append(
            run.getId(),
            "run.started",
            Map.of("status", "running"));
    RuntimeEventEnvelope second = service.append(
            run.getId(),
            "task.completed",
            Map.of("task_id", "task-1", "node_id", "need_analysis"));

    assertThat(service.replay(run.getId(), null)).extracting(RuntimeEventEnvelope::eventType)
            .containsExactly("run.started", "task.completed");
    assertThat(service.replay(run.getId(), first.id())).extracting(RuntimeEventEnvelope::id)
            .containsExactly(second.id());
    assertThat(repository.findById(second.id()).orElseThrow().getPayload()).contains("\"task_id\"");
}
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=RuntimeEventServiceTest test
```

Expected: compilation fails because `RuntimeEventService` and `RuntimeEventEnvelope` do not exist.

- [ ] **Step 3: Implement minimal runtime event service**

Create an envelope record with Jackson names for `run_id`, `event_type`, and `created_at`. Create a service that uses `ObjectMapper` to serialize payload maps, saves `RuntimeEvent`, parses payload text back into a map, and implements `replay(runId, afterId)` by loading ordered events and skipping through `afterId`.

- [ ] **Step 4: Verify GREEN**

Run:

```powershell
cd backend-java
mvn -Dtest=RuntimeEventServiceTest test
```

Expected: tests pass.

---

### Task 2: SSE Stream Endpoint

**Files:**
- Create: `backend-java/src/test/java/com/xiaoc/workbench/event/api/EventControllerTest.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/event/service/RuntimeEventStreamService.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/event/api/EventController.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java`

- [ ] **Step 1: Write failing SSE controller test**

Test behavior:

```java
@Test
void opensEventStreamWithoutAuthenticationAndUsesLastEventId() throws Exception {
    SseEmitter emitter = new SseEmitter();
    emitter.send(SseEmitter.event()
            .id("event-2")
            .name("task.completed")
            .data(Map.of("id", "event-2")));
    emitter.complete();
    when(streamService.open("run-1", "event-1")).thenReturn(emitter);

    MvcResult result = mockMvc.perform(get("/api/events/stream")
                    .param("run_id", "run-1")
                    .header("Last-Event-ID", "event-1"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("text/event-stream")))
            .andExpect(content().string(containsString("event-2")));
}
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=EventControllerTest test
```

Expected: compilation fails because event API classes do not exist.

- [ ] **Step 3: Implement stream service and controller**

`RuntimeEventStreamService.open(runId, afterId)` creates an `SseEmitter`, sends replay events from `RuntimeEventService`, registers the emitter by run ID, and removes it on completion, timeout, error, or send failure. `RuntimeEventService.append(...)` publishes to this stream service after commit.

- [ ] **Step 4: Verify GREEN**

Run:

```powershell
cd backend-java
mvn -Dtest=EventControllerTest test
```

Expected: test passes.

---

### Task 3: Audit Log Query API

**Files:**
- Create: `backend-java/src/test/java/com/xiaoc/workbench/governance/api/AuditLogControllerTest.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/governance/api/AuditLogSummary.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/governance/api/AuditLogController.java`
- Create: `backend-java/src/main/java/com/xiaoc/workbench/governance/service/AuditLogService.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/governance/repository/AuditLogRepository.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/common/security/SecurityConfig.java`

- [ ] **Step 1: Write failing audit controller tests**

Cover actor query, target query, and invalid query:

```java
@Test
void listsAuditLogsByActorWithoutAuthentication() throws Exception {
    when(auditLogService.findByActor("local-user")).thenReturn(List.of(summary()));

    mockMvc.perform(get("/api/audit-logs").param("actor_id", "local-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].action").value("HUMAN_GATE_APPROVE"));
}
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=AuditLogControllerTest test
```

Expected: compilation fails because audit API/service classes do not exist.

- [ ] **Step 3: Implement audit service and controller**

`AuditLogService.record(...)` saves `AuditLog` with a generated ID. Query methods return `AuditLogSummary` records ordered newest first. The controller accepts either `actor_id` or both `target_type` and `target_id`; otherwise it returns HTTP 400 using `IllegalArgumentException`.

- [ ] **Step 4: Verify GREEN**

Run:

```powershell
cd backend-java
mvn -Dtest=AuditLogControllerTest test
```

Expected: tests pass.

---

### Task 4: Wire Runner And Project Operations To Events And Audit

**Files:**
- Create: `backend-java/src/test/java/com/xiaoc/workbench/orchestrator/service/RunnerEventAuditIntegrationTest.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java`
- Modify: `backend-java/src/main/java/com/xiaoc/workbench/orchestrator/service/RunnerService.java`
- Update tests that import these services directly.

- [ ] **Step 1: Write failing integration tests**

Test that project creation, run start, approve, and reject create expected runtime event and audit rows:

```java
@Test
void recordsEventsAndAuditForStartAndApproval() {
    seeder.seedBuiltinAgents();
    ProjectStateResponse created = projectService.createProject("credit card installment campaign approval");
    ProjectStateResponse waiting = runnerService.startRun(created.run().id());
    runnerService.approveGate(waiting.humanGate().id(), "scope confirmed", "local-user");

    assertThat(runtimeEventRepository.findAllByRunIdOrderByCreatedAtAscIdAsc(created.run().id()))
            .extracting(RuntimeEvent::getEventType)
            .contains("project.created", "run.started", "task.completed",
                    "human_gate.waiting", "human_gate.approved", "run.completed");
    assertThat(auditLogRepository.findAllByActorIdOrderByCreatedAtDescIdDesc("local-user"))
            .extracting(AuditLog::getAction)
            .contains("PROJECT_CREATE", "RUN_START", "HUMAN_GATE_APPROVE", "RUN_COMPLETED");
}
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd backend-java
mvn -Dtest=RunnerEventAuditIntegrationTest test
```

Expected: tests fail because services are not wired into project creation and runner operations yet.

- [ ] **Step 3: Implement event and audit calls**

Call `runtimeEventService.append(...)` and `auditLogService.record(...)` at state transitions that create key operation facts. Do not emit duplicate events when `startRun` returns an already stopped run.

- [ ] **Step 4: Verify GREEN**

Run:

```powershell
cd backend-java
mvn -Dtest=RunnerEventAuditIntegrationTest,RunnerServiceTest,ProjectApplicationServiceTest test
```

Expected: tests pass.

---

### Task 5: Learning Document And Final Verification

**Files:**
- Create: `docs/learning/phase-04-sse-event-audit.md`

- [ ] **Step 1: Write the learning document**

Include these sections:

- how to use the document;
- learning goals;
- business background;
- minimum backend concepts;
- core concepts;
- design rationale;
- end-to-end flow;
- code walkthrough;
- boundary cases;
- testing strategy;
- troubleshooting guide;
- interview explanation;
- learning checklist.

- [ ] **Step 2: Verify document has no placeholder markers**

Run:

```powershell
Select-String -Path docs\learning\phase-04-sse-event-audit.md -Pattern 'T[B]D','TO[D]O','implement late[r]','fill in detail[s]'
```

Expected: no matches.

- [ ] **Step 3: Run full backend verification**

Run:

```powershell
cd backend-java
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 4: Commit**

Run:

```powershell
git add backend-java docs\learning\phase-04-sse-event-audit.md docs\superpowers\specs\2026-06-14-phase-4-sse-event-audit-design.md docs\superpowers\plans\2026-06-14-phase-4-sse-event-audit.md
git commit -m "feat: implement phase 4 sse event audit"
```
