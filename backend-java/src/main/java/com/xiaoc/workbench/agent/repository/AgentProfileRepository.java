package com.xiaoc.workbench.agent.repository;

import com.xiaoc.workbench.agent.domain.AgentProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProfileRepository extends JpaRepository<AgentProfile, String> {
    List<AgentProfile> findAllByEnabledTrueOrderBySortOrderAsc();
}
