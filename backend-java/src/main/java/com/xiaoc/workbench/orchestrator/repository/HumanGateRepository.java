package com.xiaoc.workbench.orchestrator.repository;

import com.xiaoc.workbench.orchestrator.domain.HumanGate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HumanGateRepository extends JpaRepository<HumanGate, String> {
    Optional<HumanGate> findByTaskId(String taskId);

    Optional<HumanGate> findFirstByRunIdOrderByCreatedAtDesc(String runId);
}
