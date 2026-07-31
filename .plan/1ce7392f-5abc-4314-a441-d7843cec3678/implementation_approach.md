# Implementation Approach

## Implementation Approach — Create a Task

### Layer Breakdown

**Controller — `TaskController`**
- `@RestController` + `@RequestMapping("/v1/tasks")`
- `@PostMapping` method: `createTask(@Valid @RequestBody CreateTaskRequest request, @CurrentUser UUID userId)`
- Returns `ResponseEntity<TaskResponse>` with status 201 and `Location` header
- No business logic — delegates entirely to `TaskService`

**Service — `TaskService`**
- `createTask(CreateTaskRequest request, UUID userId)` → `TaskResponse`
- Steps:
  1. **List ownership check**: `taskListRepository.findByIdAndUserId(request.listId(), userId)` — returns 404 if null (AC-6: never 403)
  2. **Calculate position**: `taskRepository.findMaxPositionByListIdAndDeletedAtIsNull(request.listId())` → `maxPosition + 1`, or `0` if list is empty
  3. **Default priority**: If `request.priority()` is null, default to `Priority.NONE` (AC-2)
  4. **Build entity**: Construct `Task` with all fields, `completedAt = null`, `version = 0`
  5. **Persist**: `taskRepository.save(task)`
  6. **Increment metric**: `meterRegistry.counter("tasks_created_total").increment()` (per locked observability NFR)
  7. **Map to response**: Convert entity → `TaskResponse` record

**Repository — `TaskRepository`**
- Extends `JpaRepository<Task, UUID>`
- Custom queries (all tenant-scoped):
  - `Optional<Task> findByIdAndUserId(UUID id, UUID userId)` — standard tenant-safe lookup
  - `@Query("SELECT COALESCE(MAX(t.position), -1) FROM Task t WHERE t.listId = :listId AND t.deletedAt IS NULL")` → `double findMaxPositionByListId(UUID listId)`
- No bare `findById` — banned by ArchUnit

### Entity — `Task`

```java
@Entity
@Table(name = "task")
public class Task {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private UUID listId;
    @Column(nullable = false, length = 500) private String title;
    @Column(columnDefinition = "TEXT") private String notes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "priority")
    private Priority priority;
    @Column(nullable = false) private double position;
    private OffsetDateTime dueAt;
    private OffsetDateTime completedAt;
    @Version private int version;
    @Column(nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
```

- **No `@ManyToOne` association to TaskList** — uses a bare `UUID listId` to avoid lazy-loading complexity. The composite FK at the DB level enforces referential integrity. JPA `@EntityGraph` is unnecessary since there's no association to fetch.
- **`@PrePersist`** sets `createdAt` and `updatedAt`; **`@PreUpdate`** refreshes `updatedAt`.

### Priority Enum

```java
public enum Priority {
    @JsonProperty("none") NONE,
    @JsonProperty("low") LOW,
    @JsonProperty("med") MED,
    @JsonProperty("high") HIGH;
}
```

- Jackson deserializes case-insensitively from JSON string → enum
- Hibernate maps to the PostgreSQL `priority` ENUM type via a custom `@Type` or `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`
- Invalid values in the request body → Jackson deserialization failure → caught by `GlobalExceptionHandler` → 422 with standard error envelope

### Position Calculation

- **Append strategy**: `MAX(position) + 1` within the list (user's choice)
- **Initial task in an empty list**: position = `0`
- **Column type**: `DOUBLE PRECISION` — writes integer values now, ready for midpoint math when the reorder story lands
- **Concurrency**: Two concurrent appends to the same list could collide on MAX. Acceptable at current scale; the unique partial index `(list_id, position) WHERE deleted_at IS NULL` would catch collisions, and a retry-on-constraint-violation is a future hardening option if needed.

### Cross-Cutting Concerns

- **X-Request-Id**: `RequestIdFilter` (in `common/`) handles propagation — no per-endpoint code needed (AC-7)
- **Metrics**: `tasks_created_total` Micrometer counter incremented after successful persist
- **Logging**: Service logs task creation at INFO level with `requestId` and `userId` in MDC
- **Transaction**: Single `save()` call — no explicit `@Transactional` needed (Spring Data default is sufficient)
