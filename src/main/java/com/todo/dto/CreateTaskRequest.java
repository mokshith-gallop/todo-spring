package com.todo.dto;

import com.todo.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        @NotNull(message = "List ID is required")
        UUID listId,

        @Size(max = 10000, message = "Notes must not exceed 10000 characters")
        String notes,

        OffsetDateTime dueAt,

        Priority priority
) {
}
