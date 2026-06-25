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
