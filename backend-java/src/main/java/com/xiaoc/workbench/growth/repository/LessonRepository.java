package com.xiaoc.workbench.growth.repository;

import com.xiaoc.workbench.growth.domain.Lesson;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, String> {
    List<Lesson> findAllByReflectionId(String reflectionId);
}
