package com.xiaoc.workbench.orchestrator.queue;

import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.redis.enabled", havingValue = "false")
public class NoopRunConcurrencyGuard implements RunConcurrencyGuard {
    @Override
    public <T> T runWithLock(String runId, Supplier<T> callback) {
        return callback.get();
    }
}