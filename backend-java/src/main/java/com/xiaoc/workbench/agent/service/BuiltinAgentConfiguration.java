package com.xiaoc.workbench.agent.service;

import com.xiaoc.workbench.agent.repository.AgentProfileRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class BuiltinAgentConfiguration {
    @Bean
    ApplicationRunner builtinAgentStartupSeeder(ObjectProvider<AgentProfileRepository> repositories) {
        return args -> repositories.ifAvailable(repository -> new BuiltinAgentSeeder(repository).seedBuiltinAgents());
    }
}
