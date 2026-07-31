package com.todo.controller;

import com.todo.common.CurrentUser;
import com.todo.dto.CreateTaskListRequest;
import com.todo.dto.TaskListResponse;
import com.todo.service.TaskListService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/lists")
public class TaskListController {

    private final TaskListService taskListService;

    public TaskListController(TaskListService taskListService) {
        this.taskListService = taskListService;
    }

    @PostMapping
    public ResponseEntity<TaskListResponse> createList(
            @Valid @RequestBody CreateTaskListRequest request,
            @CurrentUser UUID userId) {

        TaskListResponse response = taskListService.createList(request, userId);

        URI location = URI.create("/v1/lists/" + response.id());

        return ResponseEntity.created(location).body(response);
    }
}
