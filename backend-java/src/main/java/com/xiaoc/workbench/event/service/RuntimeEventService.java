package com.xiaoc.workbench.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoc.workbench.event.api.RuntimeEventEnvelope;
import com.xiaoc.workbench.orchestrator.domain.RuntimeEvent;
import com.xiaoc.workbench.orchestrator.repository.RuntimeEventRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RuntimeEventService {
    private final RuntimeEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RuntimeEventStreamService> streamService;

    public RuntimeEventService(
            RuntimeEventRepository repository,
            ObjectMapper objectMapper,
            ObjectProvider<RuntimeEventStreamService> streamService
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.streamService = streamService;
    }

    @Transactional
    public RuntimeEventEnvelope append(String runId, String eventType, Map<String, Object> payload) {
        RuntimeEvent event = repository.saveAndFlush(new RuntimeEvent(
                id("event"),
                runId,
                eventType,
                toJson(payload)));
        RuntimeEventEnvelope envelope = toEnvelope(event);
        publishAfterCommit(envelope);
        return envelope;
    }

    @Transactional(readOnly = true)
    public List<RuntimeEventEnvelope> replay(String runId, String afterId) {
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

    RuntimeEventEnvelope toEnvelope(RuntimeEvent event) {
        return new RuntimeEventEnvelope(
                event.getId(),
                event.getRunId(),
                event.getEventType(),
                fromJson(event.getPayload()),
                event.getCreatedAt());
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Runtime event payload must be JSON serializable", exception);
        }
    }

    private Map<String, Object> fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of("raw", payload);
        }
    }

    private void publishAfterCommit(RuntimeEventEnvelope event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(event);
            }
        });
    }

    private void publish(RuntimeEventEnvelope event) {
        streamService.ifAvailable(service -> service.publish(event));
    }

    private String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
