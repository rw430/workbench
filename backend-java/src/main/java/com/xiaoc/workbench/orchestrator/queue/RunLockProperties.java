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
