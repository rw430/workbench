package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.project.api.ProjectStateResponse;

public interface RunQueue {
    ProjectStateResponse enqueueStart(String runId);
}
