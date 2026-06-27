package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.common.web.InfrastructureUnavailableException;
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
            throw new InfrastructureUnavailableException("RabbitMQ run queue unavailable: " + exception.getMessage());
        }
        return projectApplicationService.getRunState(runId);
    }
}