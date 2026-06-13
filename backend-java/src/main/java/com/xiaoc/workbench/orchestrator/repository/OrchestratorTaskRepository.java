package com.xiaoc.workbench.orchestrator.repository;

import com.xiaoc.workbench.orchestrator.domain.OrchestratorTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchestratorTaskRepository extends JpaRepository<OrchestratorTask, String> {
    List<OrchestratorTask> findAllByRunIdOrderBySortOrderAsc(String runId);
}
