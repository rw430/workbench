package com.xiaoc.workbench.project.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaoc.workbench.agent.api.AgentSummary;
import java.util.List;

public record ProjectStateResponse(
        ProjectSummary project,
        RoomSummary room,
        List<AgentSummary> agents,
        RunSummary run,
        List<TaskSummary> tasks,
        @JsonProperty("human_gate") HumanGateSummary humanGate,
        String artifact,
        String reflection,
        List<LessonSummary> lessons
) {
}
