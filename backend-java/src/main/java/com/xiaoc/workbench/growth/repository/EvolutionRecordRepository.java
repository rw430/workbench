package com.xiaoc.workbench.growth.repository;

import com.xiaoc.workbench.growth.domain.EvolutionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvolutionRecordRepository extends JpaRepository<EvolutionRecord, String> {
}
