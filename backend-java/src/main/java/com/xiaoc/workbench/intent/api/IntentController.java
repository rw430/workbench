package com.xiaoc.workbench.intent.api;

import com.xiaoc.workbench.intent.service.IntentAnalysis;
import com.xiaoc.workbench.intent.service.IntentAnalysisService;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intent")
public class IntentController {
    private final IntentAnalysisService intentAnalysisService;

    public IntentController(IntentAnalysisService intentAnalysisService) {
        this.intentAnalysisService = intentAnalysisService;
    }

    @PostMapping("/analyze")
    public IntentAnalysisResponse analyze(@Valid @RequestBody AnalyzeIntentRequest request) {
        IntentAnalysis analysis = intentAnalysisService.analyze(request.goal());
        return new IntentAnalysisResponse(
                analysis.mode().toLowerCase(Locale.ROOT),
                analysis.templateId(),
                analysis.domain(),
                analysis.riskLevel().toLowerCase(Locale.ROOT),
                analysis.humanGateRequired(),
                analysis.confidence(),
                analysis.candidateRoles());
    }
}
