package com.xiaoc.workbench.orchestrator.repository;

import com.xiaoc.workbench.orchestrator.domain.RuntimeEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeEventRepository extends JpaRepository<RuntimeEvent, String> {
    List<RuntimeEvent> findAllByRunIdOrderByCreatedAtAscIdAsc(String runId);
}
