package com.xiaoc.workbench.agent.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoc.workbench.agent.service.AgentRecommendationService;
import com.xiaoc.workbench.common.security.SecurityConfig;
import com.xiaoc.workbench.common.web.ApiExceptionHandler;
import com.xiaoc.workbench.intent.service.IntentAnalysis;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AgentController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class AgentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentRecommendationService recommendationService;

    @MockBean
    private IntentAnalysisService intentAnalysisService;

    @Test
    void listsAgents() throws Exception {
        when(recommendationService.listEnabled()).thenReturn(List.of(
                new AgentSummary(
                        "agent-pd-default",
                        "需求分析分身",
                        "PD",
                        List.of("PRD"),
                        new BigDecimal("0.9500"),
                        "命中信用卡分期活动")));

        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("PD"))
                .andExpect(jsonPath("$[0].skills[0]").value("PRD"));
    }

    @Test
    void recommendsAgentsForGoal() throws Exception {
        IntentAnalysis analysis = new IntentAnalysis(
                "TASKS",
                "credit_card_installment_campaign_v1",
                "banking_credit_card",
                "MEDIUM",
                true,
                0.92,
                List.of("PD"));
        when(intentAnalysisService.analyze("银行信用卡分期活动")).thenReturn(analysis);
        when(recommendationService.recommend(analysis)).thenReturn(List.of(
                new AgentSummary(
                        "agent-pd-default",
                        "需求分析分身",
                        "PD",
                        List.of("PRD"),
                        new BigDecimal("0.9500"),
                        "命中信用卡分期活动")));

        mockMvc.perform(post("/api/agents/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"银行信用卡分期活动\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("agent-pd-default"));
    }
}
