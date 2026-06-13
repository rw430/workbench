package com.xiaoc.workbench.project.api;

import com.xiaoc.workbench.project.service.ProjectApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectApplicationService projectApplicationService;

    public ProjectController(ProjectApplicationService projectApplicationService) {
        this.projectApplicationService = projectApplicationService;
    }

    @PostMapping
    public ProjectStateResponse createProject(@Valid @RequestBody CreateProjectRequest request) {
        return projectApplicationService.createProject(request.goal());
    }

    @GetMapping("/{projectId}")
    public ProjectStateResponse getProject(@PathVariable String projectId) {
        return projectApplicationService.getProject(projectId);
    }
}
