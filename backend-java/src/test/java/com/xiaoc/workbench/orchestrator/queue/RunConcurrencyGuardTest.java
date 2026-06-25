package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunConcurrencyGuardTest {
    @Test
    void noopGuardRunsCallbackAndReturnsValue() {
        RunConcurrencyGuard guard = new NoopRunConcurrencyGuard();

        String result = guard.runWithLock("run-1", () -> "started");

        assertThat(result).isEqualTo("started");
    }
}