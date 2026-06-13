package com.xiaoc.workbench.orchestrator.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoc.workbench.common.security.SecurityConfig;
import com.xiaoc.workbench.common.web.ApiExceptionHandler;
import com.xiaoc.workbench.common.web.InvalidStateException;
import com.xiaoc.workbench.orchestrator.service.RunnerService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HumanGateController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class HumanGateControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RunnerService runnerService;

    @Test
    void approvesGateWithoutAuthenticationForLocalDemo() throws Exception {
        when(runnerService.approveGate("gate-1", "scope confirmed", "local-user"))
                .thenReturn(phase3State("completed", "approved"));

        mockMvc.perform(post("/api/human-gates/gate-1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"scope confirmed\",\"decided_by\":\"local-user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.status").value("completed"))
                .andExpect(jsonPath("$.human_gate.status").value("approved"));
    }

    @Test
    void rejectsGateWithoutAuthenticationForLocalDemo() throws Exception {
        when(runnerService.rejectGate("gate-1", "scope too broad", "local-user"))
                .thenReturn(phase3State("failed", "rejected"));

        mockMvc.perform(post("/api/human-gates/gate-1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"scope too broad\",\"decided_by\":\"local-user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.status").value("failed"))
                .andExpect(jsonPath("$.human_gate.status").value("rejected"));
    }

    @Test
    void mapsInvalidGateStateToConflict() throws Exception {
        when(runnerService.approveGate("gate-1", "scope confirmed", "local-user"))
                .thenThrow(new InvalidStateException("Gate was rejected"));

        mockMvc.perform(post("/api/human-gates/gate-1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"scope confirmed\",\"decided_by\":\"local-user\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("invalid_state"));
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
