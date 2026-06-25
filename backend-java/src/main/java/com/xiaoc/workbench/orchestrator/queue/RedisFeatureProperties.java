package com.xiaoc.workbench.orchestrator.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xiaoc.redis")
public record RedisFeatureProperties(boolean enabled, boolean failOpen) {
}
