package com.todo.repository;

import com.todo.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT COALESCE(MAX(t.position), -1) FROM Task t WHERE t.listId = :listId AND t.deletedAt IS NULL")
    double findMaxPositionByListId(@Param("listId") UUID listId);
}
