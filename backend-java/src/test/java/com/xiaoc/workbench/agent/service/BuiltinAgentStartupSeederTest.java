package com.xiaoc.workbench.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaoc.workbench.agent.domain.AgentProfile;
import com.xiaoc.workbench.agent.repository.AgentProfileRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuiltinAgentStartupSeederTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("xiaoc_test")
            .withUsername("xiaoc")
            .withPassword("xiaoc");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AgentProfileRepository repository;

    @Test
    void seedsBuiltinAgentsWhenApplicationStarts() {
        List<AgentProfile> agents = repository.findAllByEnabledTrueOrderBySortOrderAsc();

        assertThat(agents).hasSize(5);
        assertThat(agents).extracting(AgentProfile::getRole)
                .containsExactly("PD", "DEV", "QA", "RISK", "PMO");
    }
}
