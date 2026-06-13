package com.xiaoc.workbench.agent.service;

import com.xiaoc.workbench.agent.repository.AgentProfileRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class BuiltinAgentConfiguration {
    @Bean
    @ConditionalOnBean(AgentProfileRepository.class)
    BuiltinAgentSeeder builtinAgentSeeder(AgentProfileRepository repository) {
        return new BuiltinAgentSeeder(repository);
    }

    @Bean
    @ConditionalOnBean(BuiltinAgentSeeder.class)
    ApplicationRunner builtinAgentStartupSeeder(BuiltinAgentSeeder seeder) {
        return args -> seeder.seedBuiltinAgents();
    }
}
