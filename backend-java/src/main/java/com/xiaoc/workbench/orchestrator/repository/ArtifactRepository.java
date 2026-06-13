package com.xiaoc.workbench.orchestrator.repository;

import com.xiaoc.workbench.orchestrator.domain.Artifact;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactRepository extends JpaRepository<Artifact, String> {
    Optional<Artifact> findByRunId(String runId);
}
