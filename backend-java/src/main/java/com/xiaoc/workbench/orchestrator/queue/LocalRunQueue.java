package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.orchestrator.service.RunnerService;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.queue.mode", havingValue = "local", matchIfMissing = true)
public class LocalRunQueue implements RunQueue {
    private final RunnerService runnerService;
    private final RunConcurrencyGuard runConcurrencyGuard;

    public LocalRunQueue(RunnerService runnerService, RunConcurrencyGuard runConcurrencyGuard) {
        this.runnerService = runnerService;
        this.runConcurrencyGuard = runConcurrencyGuard;
    }

    @Override
    public ProjectStateResponse enqueueStart(String runId) {
        return runConcurrencyGuard.runWithLock(runId, () -> runnerService.startRun(runId));
    }
}