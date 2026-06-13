package com.xiaoc.workbench.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityConfigTest.SliceHealthController.class)
@Import({SecurityConfig.class, SecurityConfigTest.SliceHealthController.class})
class SecurityConfigTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void permitsApiHealthWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void requiresAuthenticationForOtherApiRequests() throws Exception {
        mockMvc.perform(get("/api/private-missing"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    @RequestMapping("/api")
    public static class SliceHealthController {
        @GetMapping("/health")
        Map<String, String> health() {
            return Map.of("status", "ok");
        }
    }
}

@WebMvcTest(DefaultSecurityRiskTest.SliceHealthController.class)
@Import(DefaultSecurityRiskTest.SliceHealthController.class)
class DefaultSecurityRiskTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void defaultSecurityInterceptsApiHealthBeforeMvc() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    @RequestMapping("/api")
    public static class SliceHealthController {
        @GetMapping("/health")
        Map<String, String> health() {
            return Map.of("status", "ok");
        }
    }
}
