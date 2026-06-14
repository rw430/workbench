package com.xiaoc.workbench.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/agents").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/agents/recommend").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/intent/analyze").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/projects").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/projects/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events/stream").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/audit-logs").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/runs/*/start").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/human-gates/*/approve").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/human-gates/*/reject").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
