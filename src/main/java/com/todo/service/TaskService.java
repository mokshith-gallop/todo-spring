package com.todo.service;

import com.todo.dto.CreateTaskRequest;
import com.todo.dto.TaskResponse;
import com.todo.exception.NotFoundException;
import com.todo.model.Priority;
import com.todo.model.Task;
import com.todo.repository.TaskListRepository;
import com.todo.repository.TaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskListRepository taskListRepository;
    private final MeterRegistry meterRegistry;

    public TaskService(TaskRepository taskRepository,
                       TaskListRepository taskListRepository,
                       MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.taskListRepository = taskListRepository;
        this.meterRegistry = meterRegistry;
    }

    public TaskResponse createTask(CreateTaskRequest request, UUID userId) {
        // 1. Verify list ownership — 404 if not found or not owned (AC-6)
        taskListRepository.findByIdAndUserId(request.listId(), userId)
                .orElseThrow(() -> new NotFoundException("List not found"));

        // 2. Calculate position — append at end of list
        double maxPosition = taskRepository.findMaxPositionByListId(request.listId());
        double position = maxPosition + 1; // -1 + 1 = 0 for empty list

        // 3. Default priority to NONE if null (AC-2)
        Priority priority = request.priority() != null ? request.priority() : Priority.NONE;

        // 4. Build entity
        Task task = new Task(
                userId,
                request.listId(),
                request.title(),
                request.notes(),
                priority,
                position,
                request.dueAt()
        );

        // 5. Persist
        task = taskRepository.save(task);

        // 6. Increment metric
        meterRegistry.counter("tasks_created_total").increment();

        log.info("Task created: id={}, listId={}, userId={}", task.getId(), task.getListId(), userId);

        // 7. Map to response
        return toResponse(task);
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getListId(),
                task.getNotes(),
                task.getDueAt(),
                task.getPriority(),
                task.getPosition(),
                task.getCompletedAt(),
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
