package com.xiaoc.workbench.project.repository;

import com.xiaoc.workbench.project.domain.Room;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, String> {
    Optional<Room> findByProjectId(String projectId);
}
