package com.todo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskListRequest(
        @NotBlank(message = "Name must not be blank")
        @Size(max = 120, message = "Name must not exceed 120 characters")
        String name
) {
}
