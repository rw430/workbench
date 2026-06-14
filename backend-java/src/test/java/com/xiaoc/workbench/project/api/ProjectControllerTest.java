package com.xiaoc.workbench.project.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoc.workbench.agent.api.AgentSummary;
import com.xiaoc.workbench.common.security.SecurityConfig;
import com.xiaoc.workbench.common.web.ApiExceptionHandler;
import com.xiaoc.workbench.project.service.ProjectApplicationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class ProjectControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectApplicationService projectApplicationService;

    @Test
    void createsProjectWithoutAuthenticationForLocalDemo() throws Exception {
        ProjectStateResponse state = phase2State();
        when(projectApplicationService.createProject("银行信用卡分期活动审批研发方案")).thenReturn(state);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"银行信用卡分期活动审批研发方案\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.status").value("created"))
                .andExpect(jsonPath("$.run.template_id").value("credit_card_installment_campaign_v1"))
                .andExpect(jsonPath("$.tasks[1].depends_on[0]").value("need_analysis"))
                .andExpect(jsonPath("$.human_gate").value(nullValue()));
    }

    @Test
    void getsProjectWithoutAuthenticationForLocalDemo() throws Exception {
        ProjectStateResponse state = phase2State();
        when(projectApplicationService.getProject("project-1")).thenReturn(state);

        mockMvc.perform(get("/api/projects/project-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.id").value("project-1"))
                .andExpect(jsonPath("$.tasks[0].status").value("ready"));
    }

    @Test
    void rejectsBlankProjectGoal() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void returnsNotFoundForMissingProject() throws Exception {
        when(projectApplicationService.getProject("missing")).thenThrow(new NoSuchElementException("Project not found"));

        mockMvc.perform(get("/api/projects/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    private static ProjectStateResponse phase2State() {
        return new ProjectStateResponse(
                new ProjectSummary("project-1", "银行信用卡分期活动审批研发方案", "tasks", "created"),
                new RoomSummary("room-1", "project-1", "信用卡分期活动研发协同室"),
                List.of(new AgentSummary(
                        "agent-pd-default",
                        "需求分析分身",
                        "PD",
                        List.of("PRD"),
                        new BigDecimal("0.9500"),
                        "命中信用卡分期活动")),
                new RunSummary("run-1", "project-1", "credit_card_installment_campaign_v1", "created"),
                List.of(
                        new TaskSummary(
                                "task-1",
                                "run-1",
                                "need_analysis",
                                "需求分析",
                                "llm_prd_draft",
                                "PD",
                                List.of(),
                                "ready",
                                "",
                                ""),
                        new TaskSummary(
                                "task-2",
                                "run-1",
                                "human_gate_prd",
                                "PRD 范围确认",
                                "human_gate",
                                "USER",
                                List.of("need_analysis"),
                                "pending",
                                "",
                                "")),
                null,
                null,
                null,
                List.of());
    }
}
