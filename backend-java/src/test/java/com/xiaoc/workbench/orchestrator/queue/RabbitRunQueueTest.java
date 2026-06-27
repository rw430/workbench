package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.service.ProjectApplicationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitRunQueueTest {
    @Test
    void publishesRunStartMessageAndReturnsCurrentState() {
        RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
        ProjectApplicationService projectService = Mockito.mock(ProjectApplicationService.class);
        RabbitRunQueueProperties properties = new RabbitRunQueueProperties(
                true,
                "xiaoc.run",
                "xiaoc.run.start",
                "run.start");
        ProjectStateResponse state = Mockito.mock(ProjectStateResponse.class);
        when(projectService.getRunState("run-1")).thenReturn(state);
        RabbitRunQueue queue = new RabbitRunQueue(rabbitTemplate, projectService, properties);

        ProjectStateResponse returned = queue.enqueueStart("run-1");

        ArgumentCaptor<RunStartMessage> message = ArgumentCaptor.forClass(RunStartMessage.class);
        verify(rabbitTemplate).convertAndSend(eq("xiaoc.run"), eq("run.start"), message.capture());
        assertThat(message.getValue().runId()).isEqualTo("run-1");
        assertThat(message.getValue().requestedBy()).isEqualTo("local-user");
        assertThat(message.getValue().traceId()).startsWith("run-start-");
        assertThat(returned).isSameAs(state);
    }
}