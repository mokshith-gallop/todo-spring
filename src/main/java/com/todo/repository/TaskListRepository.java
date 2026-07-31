package com.todo.repository;

import com.todo.model.TaskList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskListRepository extends JpaRepository<TaskList, UUID> {

    Optional<TaskList> findByIdAndUserId(UUID id, UUID userId);
}
