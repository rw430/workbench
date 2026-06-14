package com.xiaoc.workbench.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoc.workbench.event.api.RuntimeEventEnvelope;
import com.xiaoc.workbench.orchestrator.domain.RuntimeEvent;
import com.xiaoc.workbench.orchestrator.repository.RuntimeEventRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RuntimeEventStreamService {
    private final RuntimeEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByRun = new ConcurrentHashMap<>();

    public RuntimeEventStreamService(RuntimeEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public SseEmitter open(String runId, String afterId) {
        SseEmitter emitter = new SseEmitter(0L);
        replay(runId, afterId).forEach(event -> send(emitter, event));
        register(runId, emitter);
        return emitter;
    }

    public void publish(RuntimeEventEnvelope event) {
        emittersByRun.getOrDefault(event.runId(), new CopyOnWriteArrayList<>())
                .removeIf(emitter -> !send(emitter, event));
    }

    private void register(String runId, SseEmitter emitter) {
        emittersByRun.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        emitter.onError(throwable -> remove(runId, emitter));
    }

    private void remove(String runId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByRun.get(runId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    private boolean send(SseEmitter emitter, RuntimeEventEnvelope event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(event.id())
                    .name(event.eventType())
                    .data(event));
            return true;
        } catch (IOException | IllegalStateException exception) {
            emitter.completeWithError(exception);
            return false;
        }
    }

    private List<RuntimeEventEnvelope> replay(String runId, String afterId) {
        List<RuntimeEvent> events = repository.findAllByRunIdOrderByCreatedAtAscIdAsc(runId);
        if (afterId == null || afterId.isBlank()) {
            return events.stream().map(this::toEnvelope).toList();
        }
        return events.stream()
                .dropWhile(event -> !event.getId().equals(afterId))
                .skip(1)
                .map(this::toEnvelope)
                .toList();
    }

    private RuntimeEventEnvelope toEnvelope(RuntimeEvent event) {
        return new RuntimeEventEnvelope(
                event.getId(),
                event.getRunId(),
                event.getEventType(),
                fromJson(event.getPayload()),
                event.getCreatedAt());
    }

    private Map<String, Object> fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of("raw", payload);
        }
    }
}
