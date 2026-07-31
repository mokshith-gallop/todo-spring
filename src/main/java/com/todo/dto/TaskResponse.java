package com.todo.dto;

import com.todo.model.Priority;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        UUID listId,
        String notes,
        OffsetDateTime dueAt,
        Priority priority,
        double position,
        OffsetDateTime completedAt,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
