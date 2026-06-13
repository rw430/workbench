package com.xiaoc.workbench.orchestrator.api;

import com.xiaoc.workbench.orchestrator.service.RunnerService;
import com.xiaoc.workbench.project.api.ProjectStateResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/human-gates")
public class HumanGateController {
    private final RunnerService runnerService;

    public HumanGateController(RunnerService runnerService) {
        this.runnerService = runnerService;
    }

    @PostMapping("/{gateId}/approve")
    public ProjectStateResponse approve(
            @PathVariable String gateId,
            @Valid @RequestBody HumanGateDecisionRequest request
    ) {
        return runnerService.approveGate(gateId, request.reason(), request.effectiveDecidedBy());
    }

    @PostMapping("/{gateId}/reject")
    public ProjectStateResponse reject(
            @PathVariable String gateId,
            @Valid @RequestBody HumanGateDecisionRequest request
    ) {
        return runnerService.rejectGate(gateId, request.reason(), request.effectiveDecidedBy());
    }
}
