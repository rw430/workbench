package com.xiaoc.workbench.project.repository;

import com.xiaoc.workbench.project.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, String> {
}
