# Validation

## Validation Rules — Create a Task

### Validation Layers

Validation is enforced at **three levels** for defense in depth:

| Layer | Mechanism | Purpose |
|-------|-----------|---------|
| **DTO (Bean Validation)** | `@NotBlank`, `@Size`, `@NotNull` on `CreateTaskRequest` | Fast-fail before service logic; produces 422 |
| **Service (business rules)** | List ownership check, priority defaulting | Tenant isolation, business defaults |
| **Database (constraints)** | CHECK constraints, composite FK, ENUM type | Last line of defense; prevents corrupt data even if app has a bug |

### Field-by-Field Rules

**`title`** (AC-1, AC-4):
- **DTO**: `@NotBlank` (rejects null, empty, and whitespace-only) + `@Size(max = 500)`
- **DB**: `CHECK (length(trim(title)) > 0)` + `CHECK (length(title) <= 500)`
- **Error**: 422 → `{ "field": "title", "message": "Title must be between 1 and 500 characters" }`
- **Edge case**: A string of 500 spaces fails `@NotBlank` (not blank after trim). A title of exactly 500 non-blank chars succeeds.

**`listId`** (AC-1, AC-6):
- **DTO**: `@NotNull` — rejects missing field with 422
- **Service**: `taskListRepository.findByIdAndUserId(listId, userId)` → if null, throw `NotFoundException` → 404
- **Key security rule**: Returns 404 (never 403) whether the list doesn't exist or belongs to another user — prevents tenant enumeration
- **DB**: Composite FK `(list_id, user_id) → task_list(id, user_id)` — DB-level backstop

**`notes`** (AC-3):
- **DTO**: `@Size(max = 10000)` — nullable, so null/absent is fine
- **DB**: `CHECK (notes IS NULL OR length(notes) <= 10000)`
- **Error**: 422 → `{ "field": "notes", "message": "Notes must not exceed 10000 characters" }`

**`dueAt`** (AC-5):
- **DTO**: No annotation beyond type — `OffsetDateTime` handles parsing
- **Jackson**: Configured to deserialize ISO-8601 strings; malformed input → `HttpMessageNotReadableException` → 422
- **Storage**: Stored as `TIMESTAMPTZ` (UTC); returned as ISO-8601 `OffsetDateTime`

**`priority`** (AC-2):
- **DTO**: No `@NotNull` — null is allowed (means "use default")
- **Service**: `priority == null ? Priority.NONE : priority`
- **Jackson**: Invalid enum value (e.g., `"urgent"`) → `InvalidFormatException` → caught by `GlobalExceptionHandler` → 422 with message listing valid values
- **DB**: PostgreSQL ENUM type rejects invalid values at the DB level

### Error Handling Flow

```
Request → Jackson deserialization
  ├─ Malformed JSON → 400 (bad request)
  ├─ Invalid type/enum → 422 (caught by GlobalExceptionHandler)
  └─ Valid parse → Bean Validation (@Valid)
       ├─ Constraint violation → 422 with field-level details
       └─ Valid → TaskService
            ├─ List not found/not owned → 404
            └─ Success → 201
```

### GlobalExceptionHandler Mappings

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `MethodArgumentNotValidException` | 422 | `VALIDATION_ERROR` |
| `HttpMessageNotReadableException` | 422 | `VALIDATION_ERROR` |
| `NotFoundException` (custom) | 404 | `NOT_FOUND` |
| `AccessDeniedException` (Spring Security) | 401 | `UNAUTHORIZED` |
| Unhandled exceptions | 500 | `INTERNAL_ERROR` |

### Key Design Choices

- **422 for all validation errors** (not 400) — 400 is reserved for truly malformed requests (unparseable JSON). 422 means "parseable but semantically invalid," which matches the acceptance criteria language.
- **Field-level detail array** — `details` is an array of `{ field, message }` objects, supporting multiple validation errors in a single response.
- **No custom validator annotations** — all constraints are satisfied by standard Bean Validation annotations. The priority defaulting is a simple null-coalesce in the service, not a validation concern.
