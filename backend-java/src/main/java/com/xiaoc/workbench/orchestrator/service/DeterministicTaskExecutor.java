package com.xiaoc.workbench.orchestrator.service;

import com.xiaoc.workbench.orchestrator.domain.OrchestratorTask;
import org.springframework.stereotype.Service;

@Service
public class DeterministicTaskExecutor {
    public String execute(OrchestratorTask task) {
        return switch (task.getKind()) {
            case "LLM_PRD_DRAFT" -> "PRD draft for " + task.getName()
                    + ": scope, roles, risks, and acceptance criteria.";
            case "LLM_RISK_REVIEW" -> "risk review for " + task.getName()
                    + ": compliance boundaries and audit notes.";
            case "LLM_TECH_DESIGN" -> "technical design for " + task.getName()
                    + ": API, state machine, and persistence plan.";
            case "LLM_TEST_PLAN" -> "test plan for " + task.getName()
                    + ": unit, integration, and acceptance coverage.";
            case "DELIVERY_SUMMARY" -> "delivery summary for " + task.getName()
                    + ": completed outputs and remaining checks.";
            case "REFLECTION" -> "reflection for " + task.getName()
                    + ": what worked, what to improve, and reusable lessons.";
            case "LESSONS_EXTRACT" -> "lessons for " + task.getName()
                    + ": reusable workflow and interview talking points.";
            default -> "deterministic output for " + task.getKind() + " task " + task.getName() + ".";
        };
    }
}
