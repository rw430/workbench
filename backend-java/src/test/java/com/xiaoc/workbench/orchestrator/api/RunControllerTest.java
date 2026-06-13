package com.xiaoc.workbench.orchestrator.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoc.workbench.common.security.SecurityConfig;
import com.xiaoc.workbench.common.web.ApiExceptionHandler;
import com.xiaoc.workbench.orchestrator.queue.RunQueue;
import com.xiaoc.workbench.project.api.HumanGateSummary;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import com.xiaoc.workbench.project.api.ProjectSummary;
import com.xiaoc.workbench.project.api.RoomSummary;
import com.xiaoc.workbench.project.api.RunSummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RunController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class RunControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RunQueue runQueue;

    @Test
    void startsRunWithoutAuthenticationForLocalDemo() throws Exception {
        when(runQueue.enqueueStart("run-1")).thenReturn(phase3State("waiting_human", "waiting"));

        mockMvc.perform(post("/api/runs/run-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.status").value("waiting_human"))
                .andExpect(jsonPath("$.human_gate.status").value("waiting"));
    }

    private static ProjectStateResponse phase3State(String runStatus, String gateStatus) {
        return new ProjectStateResponse(
                new ProjectSummary("project-1", "goal", "tasks", runStatus),
                new RoomSummary("room-1", "project-1", "room"),
                List.of(),
                new RunSummary("run-1", "project-1", "credit_card_installment_campaign_v1", runStatus),
                List.of(),
                new HumanGateSummary("gate-1", "run-1", "task-2", gateStatus, "Confirm scope."),
                null,
                null);
    }
}
