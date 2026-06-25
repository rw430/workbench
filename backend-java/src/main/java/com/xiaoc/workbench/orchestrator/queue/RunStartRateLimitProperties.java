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
