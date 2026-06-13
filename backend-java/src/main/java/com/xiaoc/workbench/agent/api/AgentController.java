package com.xiaoc.workbench.agent.api;

import com.xiaoc.workbench.agent.service.AgentRecommendationService;
import com.xiaoc.workbench.intent.service.IntentAnalysis;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentRecommendationService recommendationService;
    private final IntentAnalysisService intentAnalysisService;

    public AgentController(
            AgentRecommendationService recommendationService,
            IntentAnalysisService intentAnalysisService
    ) {
        this.recommendationService = recommendationService;
        this.intentAnalysisService = intentAnalysisService;
    }

    @GetMapping
    public List<AgentSummary> listAgents() {
        return recommendationService.listEnabled();
    }

    @PostMapping("/recommend")
    public List<AgentSummary> recommend(@Valid @RequestBody RecommendAgentsRequest request) {
        IntentAnalysis analysis = intentAnalysisService.analyze(request.goal());
        return recommendationService.recommend(analysis);
    }
}
