package com.todo.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskListResponse(
        UUID id,
        String name,
        boolean isInbox,
        double position,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
