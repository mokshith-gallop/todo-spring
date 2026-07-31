package com.todo.repository;

import com.todo.model.TaskList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskListRepository extends JpaRepository<TaskList, UUID> {

    Optional<TaskList> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT COALESCE(MAX(tl.position), -1) FROM TaskList tl WHERE tl.userId = :userId AND tl.deletedAt IS NULL")
    double findMaxPositionByUserId(@Param("userId") UUID userId);
}
