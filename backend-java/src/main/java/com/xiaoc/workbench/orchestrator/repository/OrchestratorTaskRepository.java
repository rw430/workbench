package com.xiaoc.workbench.orchestrator.repository;

import com.xiaoc.workbench.orchestrator.domain.OrchestratorTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchestratorTaskRepository extends JpaRepository<OrchestratorTask, String> {
    List<OrchestratorTask> findAllByRunIdOrderBySortOrderAsc(String runId);

    Optional<OrchestratorTask> findByRunIdAndNodeId(String runId, String nodeId);
}
