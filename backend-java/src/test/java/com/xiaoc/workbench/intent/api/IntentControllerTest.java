package com.xiaoc.workbench.intent.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoc.workbench.common.security.SecurityConfig;
import com.xiaoc.workbench.common.web.ApiExceptionHandler;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IntentController.class)
@Import({IntentAnalysisService.class, ApiExceptionHandler.class, SecurityConfig.class})
class IntentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void analyzesGoal() throws Exception {
        mockMvc.perform(post("/api/intent/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goal\":\"银行信用卡分期活动审批研发方案\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("tasks"))
            .andExpect(jsonPath("$.template_id").value("credit_card_installment_campaign_v1"))
            .andExpect(jsonPath("$.human_gate_required").value(true))
            .andExpect(jsonPath("$.candidate_roles[0]").value("PD"));
    }

    @Test
    void rejectsBlankGoal() throws Exception {
        mockMvc.perform(post("/api/intent/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"goal\":\"  \"}"))
            .andExpect(status().isBadRequest());
    }
}
