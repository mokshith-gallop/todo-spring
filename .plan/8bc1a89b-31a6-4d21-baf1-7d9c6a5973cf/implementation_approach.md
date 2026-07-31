# Implementation Approach

## Implementation Approach — Create a Task List

Follow the exact patterns established by the existing `TaskController` / `TaskService` flow.

### New Files

| File | Purpose |
|------|---------|
| `controller/TaskListController.java` | `@RestController` at `/v1/lists`, single `POST` method |
| `service/TaskListService.java` | Business logic: position calc, entity build, persist |
| `dto/CreateTaskListRequest.java` | Java record with Bean Validation annotations |
| `dto/TaskListResponse.java` | Java record mapping all `TaskList` fields |

### Flow (mirrors `TaskController.createTask`)

1. **Controller** receives `@Valid @RequestBody CreateTaskListRequest` + `@CurrentUser UUID userId`
2. **Service** calculates position via `TaskListRepository.findMaxPositionByUserId(userId)` (new query, returns `COALESCE(MAX(position), -1)` — same sentinel as tasks so first list gets position `0`)
3. **Service** constructs `TaskList(userId, name.trim(), false, position)` — `is_inbox` always `false` for user-created lists
4. **Service** persists via `taskListRepository.save(entity)`
5. **Service** maps to `TaskListResponse` and returns
6. **Controller** returns `ResponseEntity.created(URI.create("/v1/lists/" + id)).body(response)` — HTTP 201

### Repository Addition

Add to `TaskListRepository`:
```java
@Query("SELECT COALESCE(MAX(tl.position), -1) FROM TaskList tl WHERE tl.userId = :userId AND tl.deletedAt IS NULL")
double findMaxPositionByUserId(@Param("userId") UUID userId);
```

### Key Decisions
- **Name trimming**: `name.trim()` applied in the service before persisting — stored value is always trimmed
- **No metrics counter** for list creation (unlike `tasks_created_total`) — the PRD doesn't mention one; add later if needed
- **No `@Transactional`** needed — single-row insert, no multi-step operation
- **Auth**: Uses the existing `X-User-Id` header stub via `@CurrentUser` (JWT comes in a later story; AC-5 "401 for unauthenticated" is satisfied by the `CurrentUserArgumentResolver` throwing `IllegalStateException` when the header is missing — the `GlobalExceptionHandler` catches this as a 500 today; a dedicated 401 mapping should be added)

### 401 Handling Gap
The existing `CurrentUserArgumentResolver` throws `IllegalStateException` for missing/invalid `X-User-Id`, which the generic handler maps to 500. To satisfy AC-5, add a handler for this case in `GlobalExceptionHandler` that returns 401 with the standard error envelope. This aligns with how JWT auth will work later — missing credentials → 401.
