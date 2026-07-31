# API Design

## POST /v1/tasks — Create a Task

### Request

```
POST /v1/tasks
Authorization: Bearer <JWT>
X-Request-Id: <uuid>  (optional; generated if absent)
Content-Type: application/json
```

**Request body:**
```json
{
  "title": "Buy groceries",
  "listId": "a1b2c3d4-...",
  "notes": "Milk, eggs, bread",
  "dueAt": "2026-08-15T10:00:00Z",
  "priority": "med"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `title` | `string` | **yes** | 1–500 chars, not blank after trim |
| `listId` | `UUID` | **yes** | Must exist and belong to the authenticated user |
| `notes` | `string` | no | Max 10,000 chars |
| `dueAt` | `ISO-8601 OffsetDateTime` | no | Stored as UTC `timestamptz` |
| `priority` | `string` | no | One of `none`, `low`, `med`, `high`; defaults to `none` |

### Success Response — 201 Created

```json
{
  "id": "f47ac10b-...",
  "title": "Buy groceries",
  "listId": "a1b2c3d4-...",
  "notes": "Milk, eggs, bread",
  "dueAt": "2026-08-15T10:00:00Z",
  "priority": "med",
  "position": 3,
  "completedAt": null,
  "version": 0,
  "createdAt": "2026-07-30T14:22:00Z",
  "updatedAt": "2026-07-30T14:22:00Z"
}
```

**Response headers:**
- `X-Request-Id: <uuid>` — propagated from request or generated
- `Location: /v1/tasks/f47ac10b-...`

### Error Responses

**422 — Validation error** (blank/oversized title, oversized notes, invalid priority):
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [
      { "field": "title", "message": "Title must be between 1 and 500 characters" }
    ]
  }
}
```

**404 — List not found or not owned** (AC-6, prevents tenant enumeration):
```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "List not found"
  }
}
```

**401 — Missing/invalid/expired JWT:**
```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Authentication required"
  }
}
```

### DTOs (Java Records)

```java
// Request
public record CreateTaskRequest(
    @NotBlank @Size(max = 500) String title,
    @NotNull UUID listId,
    @Size(max = 10000) String notes,
    OffsetDateTime dueAt,
    Priority priority   // defaults to NONE if null
) {}

// Response
public record TaskResponse(
    UUID id, String title, UUID listId,
    String notes, OffsetDateTime dueAt,
    Priority priority, double position,
    OffsetDateTime completedAt, int version,
    OffsetDateTime createdAt, OffsetDateTime updatedAt
) {}
```

### Design Decisions

- **No `userId` in request or response** — resolved from JWT via `@CurrentUser`, implicit in all queries
- **`position` included in response** — clients need it for future drag-and-drop reordering
- **`deletedAt` NOT in response** — soft-deleted tasks are excluded from all queries; this field is internal
- **`Location` header** — standard REST practice for 201 responses; points to the newly created task resource
