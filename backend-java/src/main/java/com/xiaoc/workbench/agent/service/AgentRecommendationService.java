package com.xiaoc.workbench.agent.service;

import com.xiaoc.workbench.agent.api.AgentSummary;
import com.xiaoc.workbench.agent.domain.AgentProfile;
import com.xiaoc.workbench.agent.repository.AgentProfileRepository;
import com.xiaoc.workbench.intent.service.IntentAnalysis;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

@Service
public class AgentRecommendationService {
    private final AgentProfileRepository repository;

    public AgentRecommendationService(AgentProfileRepository repository) {
        this.repository = repository;
    }

    public List<AgentSummary> listEnabled() {
        return repository.findAllByEnabledTrueOrderBySortOrderAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    public List<AgentSummary> recommend(IntentAnalysis analysis) {
        Map<String, Integer> roleOrder = IntStream.range(0, analysis.candidateRoles().size())
                .boxed()
                .collect(Collectors.toMap(analysis.candidateRoles()::get, index -> index));

        return repository.findAllByEnabledTrueOrderBySortOrderAsc().stream()
                .filter(agent -> roleOrder.containsKey(agent.getRole()))
                .sorted(Comparator.comparingInt(agent -> roleOrder.get(agent.getRole())))
                .map(this::toSummary)
                .toList();
    }

    private AgentSummary toSummary(AgentProfile agent) {
        return new AgentSummary(
                agent.getId(),
                agent.getName(),
                agent.getRole(),
                splitSkills(agent.getSkills()),
                agent.getScore(),
                agent.getRecommendationReason());
    }

    private List<String> splitSkills(String skills) {
        return Arrays.stream(skills.split(","))
                .map(String::strip)
                .filter(skill -> !skill.isEmpty())
                .toList();
    }
}
