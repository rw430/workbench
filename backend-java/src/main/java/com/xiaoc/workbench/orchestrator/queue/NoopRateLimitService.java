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
