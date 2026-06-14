package com.xiaoc.workbench.governance.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoc.workbench.common.security.SecurityConfig;
import com.xiaoc.workbench.common.web.ApiExceptionHandler;
import com.xiaoc.workbench.governance.service.AuditLogService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditLogController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class AuditLogControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogService auditLogService;

    @Test
    void listsAuditLogsByActorWithoutAuthentication() throws Exception {
        when(auditLogService.findByActor("local-user")).thenReturn(List.of(summary()));

        mockMvc.perform(get("/api/audit-logs").param("actor_id", "local-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor_id").value("local-user"))
                .andExpect(jsonPath("$[0].action").value("HUMAN_GATE_APPROVE"));
    }

    @Test
    void listsAuditLogsByTargetWithoutAuthentication() throws Exception {
        when(auditLogService.findByTarget("human_gate", "gate-1")).thenReturn(List.of(summary()));

        mockMvc.perform(get("/api/audit-logs")
                        .param("target_type", "human_gate")
                        .param("target_id", "gate-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].target_type").value("human_gate"))
                .andExpect(jsonPath("$[0].target_id").value("gate-1"));
    }

    @Test
    void rejectsAuditQueryWithoutActorOrTarget() throws Exception {
        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    private static AuditLogSummary summary() {
        return new AuditLogSummary(
                "audit-1",
                "local-user",
                "HUMAN_GATE_APPROVE",
                "human_gate",
                "gate-1",
                Map.of("run_id", "run-1"),
                Instant.parse("2026-06-14T00:00:00Z"));
    }
}
