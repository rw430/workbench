package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xiaoc.workbench.common.web.RateLimitExceededException;
import com.xiaoc.workbench.common.web.RunAlreadyLockedException;
import com.xiaoc.workbench.support.RedisIntegrationTest;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "xiaoc.run-lock.ttl-seconds=20",
        "xiaoc.rate-limit.run-start.max-requests=2",
        "xiaoc.rate-limit.run-start.window-seconds=60"
})
class RedisQueueInfrastructureIntegrationTest extends RedisIntegrationTest {
    @Autowired
    private RunConcurrencyGuard guard;

    @Autowired
    private RateLimitService rateLimitService;

    @Test
    void redisGuardRejectsNestedStartForSameRun() {
        assertThatThrownBy(() -> guard.runWithLock("run-lock-test", () ->
                guard.runWithLock("run-lock-test", () -> "nested")))
                .isInstanceOf(RunAlreadyLockedException.class)
                .hasMessageContaining("run-lock-test");
    }

    @Test
    void redisGuardReleasesLockAfterCallback() {
        AtomicInteger count = new AtomicInteger();

        guard.runWithLock("run-lock-release-test", () -> count.incrementAndGet());
        guard.runWithLock("run-lock-release-test", () -> count.incrementAndGet());

        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    void redisRateLimiterRejectsAfterConfiguredWindowLimit() {
        rateLimitService.checkAllowed("actor-rate-test", "run-start");
        rateLimitService.checkAllowed("actor-rate-test", "run-start");

        assertThatThrownBy(() -> rateLimitService.checkAllowed("actor-rate-test", "run-start"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("run-start");
    }
}