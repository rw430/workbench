package com.xiaoc.workbench.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xiaoc.workbench.agent.domain.AgentProfile;
import com.xiaoc.workbench.agent.repository.AgentProfileRepository;
import com.xiaoc.workbench.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(BuiltinAgentSeeder.class)
class BuiltinAgentSeederTest extends PostgresIntegrationTest {
    @Autowired
    private BuiltinAgentSeeder seeder;

    @Autowired
    private AgentProfileRepository repository;

    @Test
    void seedsBuiltinAgentsIdempotently() {
        seeder.seedBuiltinAgents();
        seeder.seedBuiltinAgents();

        List<AgentProfile> agents = repository.findAllByEnabledTrueOrderBySortOrderAsc();

        assertThat(agents).hasSize(5);
        assertThat(agents).extracting(AgentProfile::getRole)
            .containsExactly("PD", "DEV", "QA", "RISK", "PMO");
        assertThat(agents.get(0).getRecommendationReason()).contains("信用卡分期活动");
        assertThat(agents.get(3).getRecommendationReason()).contains("风险边界");
    }

    @Test
    void seedsThroughExplicitRepositoryConstructor() {
        AgentProfileRepository mockRepository = mock(AgentProfileRepository.class);
        when(mockRepository.existsById(anyString())).thenReturn(false);

        new BuiltinAgentSeeder(mockRepository).seedBuiltinAgents();

        verify(mockRepository, times(5)).save(any(AgentProfile.class));
    }
}
