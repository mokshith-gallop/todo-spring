# Validation

## Validation — Name Rules & Error Handling

### Request DTO: `CreateTaskListRequest`

```java
public record CreateTaskListRequest(
    @NotBlank(message = "Name must not be blank")
    @Size(max = 120, message = "Name must not exceed 120 characters")
    String name
)
```

Single field, two constraints. `@NotBlank` handles both null and whitespace-only (AC-2). `@Size(max = 120)` handles length overflow (AC-3).

### Validation Behavior Matrix

| Input | `@NotBlank` | `@Size` | HTTP | AC |
|-------|-----------|---------|------|-----|
| `null` | ✗ | — | 422 | AC-2 |
| `""` | ✗ | — | 422 | AC-2 |
| `"   "` | ✗ | — | 422 | AC-2 |
| `"a"` | ✓ | ✓ | 201 | AC-1 |
| 120 chars | ✓ | ✓ | 201 | AC-1 |
| 121 chars | ✓ | ✗ | 422 | AC-3 |
| Missing body | — | — | 422 | — |

### Error Response Shape

Follows the existing `GlobalExceptionHandler` pattern exactly. Example for blank name:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [
      {
        "field": "name",
        "message": "Name must not be blank"
      }
    ]
  }
}
```

The existing `MethodArgumentNotValidException` handler already produces this format — no new exception handler needed for validation errors.

### 401 for Unauthenticated (AC-5)

Add a new handler in `GlobalExceptionHandler` for the `IllegalStateException` thrown by `CurrentUserArgumentResolver` when the `X-User-Id` header is missing. Map it to 401:

```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Authentication required"
  }
}
```

This requires checking the exception message to distinguish auth-related `IllegalStateException` from others. A cleaner approach: introduce a small `AuthenticationRequiredException` (unchecked), throw it from `CurrentUserArgumentResolver`, and handle it in `GlobalExceptionHandler` with a dedicated `@ExceptionHandler`.

### Defense-in-Depth

If Bean Validation is somehow bypassed, the DB constraints (`ck_task_list_name_not_blank`, `ck_task_list_name_length`, `VARCHAR(120)`) catch it. The resulting `DataIntegrityViolationException` would hit the generic 500 handler — acceptable since this path should never be reached in practice.
