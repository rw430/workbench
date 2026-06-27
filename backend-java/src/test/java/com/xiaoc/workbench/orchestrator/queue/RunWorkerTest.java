package com.xiaoc.workbench.orchestrator.queue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiaoc.workbench.orchestrator.service.RunnerService;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RunWorkerTest {
    @Test
    void consumesMessageThroughConcurrencyGuard() {
        RunnerService runnerService = Mockito.mock(RunnerService.class);
        RunConcurrencyGuard guard = Mockito.mock(RunConcurrencyGuard.class);
        RunWorker worker = new RunWorker(runnerService, guard);
        RunStartMessage message = new RunStartMessage(
                "run-1",
                "local-user",
                Instant.parse("2026-06-25T00:00:00Z"),
                "trace-1");

        when(guard.runWithLock(eq("run-1"), any())).thenAnswer(invocation -> {
            Supplier<?> callback = invocation.getArgument(1);
            callback.get();
            return null;
        });

        worker.handle(message);

        verify(guard).runWithLock(eq("run-1"), any());
        verify(runnerService).startRun("run-1");
    }
}