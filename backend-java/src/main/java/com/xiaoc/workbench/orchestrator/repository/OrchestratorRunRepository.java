package com.xiaoc.workbench.orchestrator.repository;

import com.xiaoc.workbench.orchestrator.domain.OrchestratorRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchestratorRunRepository extends JpaRepository<OrchestratorRun, String> {
    Optional<OrchestratorRun> findByProjectId(String projectId);
}
