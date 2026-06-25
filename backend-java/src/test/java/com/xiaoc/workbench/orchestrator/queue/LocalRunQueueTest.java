package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiaoc.workbench.orchestrator.service.RunnerService;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LocalRunQueueTest {
    @Test
    void runsRunnerInsideConcurrencyGuard() {
        RunnerService runnerService = Mockito.mock(RunnerService.class);
        RunConcurrencyGuard guard = Mockito.mock(RunConcurrencyGuard.class);
        ProjectStateResponse response = Mockito.mock(ProjectStateResponse.class);
        when(guard.runWithLock(eq("run-1"), any())).thenReturn(response);

        LocalRunQueue queue = new LocalRunQueue(runnerService, guard);

        assertThat(queue.enqueueStart("run-1")).isSameAs(response);
        verify(guard).runWithLock(eq("run-1"), any());
    }
}