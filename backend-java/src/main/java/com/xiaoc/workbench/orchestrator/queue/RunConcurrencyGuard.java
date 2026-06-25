package com.xiaoc.workbench.orchestrator.queue;

import java.util.function.Supplier;

public interface RunConcurrencyGuard {
    <T> T runWithLock(String runId, Supplier<T> callback);
}