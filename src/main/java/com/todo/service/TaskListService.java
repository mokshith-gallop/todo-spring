package com.todo.service;

import com.todo.dto.CreateTaskListRequest;
import com.todo.dto.TaskListResponse;
import com.todo.model.TaskList;
import com.todo.repository.TaskListRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TaskListService {

    private static final Logger log = LoggerFactory.getLogger(TaskListService.class);

    private final TaskListRepository taskListRepository;

    public TaskListService(TaskListRepository taskListRepository) {
        this.taskListRepository = taskListRepository;
    }

    public TaskListResponse createList(CreateTaskListRequest request, UUID userId) {
        // 1. Calculate position — append after the last list for this user
        double maxPosition = taskListRepository.findMaxPositionByUserId(userId);
        double position = maxPosition + 1; // -1 + 1 = 0 for first list

        // 2. Build entity — is_inbox always false for user-created lists, name trimmed
        TaskList taskList = new TaskList(userId, request.name().trim(), false, position);

        // 3. Persist
        taskList = taskListRepository.save(taskList);

        log.info("TaskList created: id={}, userId={}", taskList.getId(), userId);

        // 4. Map to response
        return toResponse(taskList);
    }

    private TaskListResponse toResponse(TaskList taskList) {
        return new TaskListResponse(
                taskList.getId(),
                taskList.getName(),
                taskList.isInbox(),
                taskList.getPosition(),
                taskList.getCreatedAt(),
                taskList.getUpdatedAt()
        );
    }
}
