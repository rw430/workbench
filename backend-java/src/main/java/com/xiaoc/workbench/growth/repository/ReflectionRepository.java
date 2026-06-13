package com.xiaoc.workbench.growth.repository;

import com.xiaoc.workbench.growth.domain.Reflection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReflectionRepository extends JpaRepository<Reflection, String> {
    Optional<Reflection> findByRunId(String runId);
}
