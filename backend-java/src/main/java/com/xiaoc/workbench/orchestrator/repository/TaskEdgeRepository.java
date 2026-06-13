package com.xiaoc.workbench.orchestrator.repository;

import com.xiaoc.workbench.orchestrator.domain.TaskEdge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskEdgeRepository extends JpaRepository<TaskEdge, String> {
    List<TaskEdge> findAllByRunId(String runId);
}
