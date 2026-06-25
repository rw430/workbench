package com.xiaoc.workbench.orchestrator.queue;

import org.junit.jupiter.api.Test;

class RateLimitServiceTest {
    @Test
    void noopRateLimiterAllowsRequests() {
        RateLimitService service = new NoopRateLimitService();

        service.checkAllowed("local-user", "run-start");
    }
}