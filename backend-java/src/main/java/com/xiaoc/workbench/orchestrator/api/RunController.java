package com.xiaoc.workbench.orchestrator.api;

import com.xiaoc.workbench.orchestrator.queue.RateLimitService;
import com.xiaoc.workbench.orchestrator.queue.RunQueue;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public class RunController {
    private final RunQueue runQueue;
    private final RateLimitService rateLimitService;

    public RunController(RunQueue runQueue, RateLimitService rateLimitService) {
        this.runQueue = runQueue;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/{runId}/start")
    public ProjectStateResponse startRun(@PathVariable String runId) {
        rateLimitService.checkAllowed("local-user", "run-start");
        return runQueue.enqueueStart(runId);
    }
}
