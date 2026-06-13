package com.xiaoc.workbench.orchestrator.repository;

import com.xiaoc.workbench.orchestrator.domain.OrchestratorRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchestratorRunRepository extends JpaRepository<OrchestratorRun, String> {
}
