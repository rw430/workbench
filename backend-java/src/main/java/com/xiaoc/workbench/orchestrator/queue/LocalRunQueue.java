package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.orchestrator.service.RunnerService;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import org.springframework.stereotype.Service;

@Service
public class LocalRunQueue implements RunQueue {
    private final RunnerService runnerService;

    public LocalRunQueue(RunnerService runnerService) {
        this.runnerService = runnerService;
    }

    @Override
    public ProjectStateResponse enqueueStart(String runId) {
        return runnerService.startRun(runId);
    }
}
