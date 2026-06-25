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
