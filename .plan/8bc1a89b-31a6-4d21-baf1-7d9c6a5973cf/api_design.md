# API Design

## API Design — POST /v1/lists

### Endpoint

`POST /api/v1/lists` (context path `/api` is set in `application.properties`)

### Request

**Headers:**
- `Content-Type: application/json` (required)
- `X-User-Id: {uuid}` (required — current auth stub; becomes `Authorization: Bearer` in JWT story)
- `X-Request-Id: {uuid}` (optional — auto-generated if absent)

**Body:**
```json
{
  "name": "Shopping"
}
```

Single field. No optional fields for create.

### Response — 201 Created

**Headers:**
- `Location: /v1/lists/{id}`
- `X-Request-Id: {uuid}`

**Body:**
```json
{
  "id": "a1b2c3d4-...",
  "name": "Shopping",
  "isInbox": false,
  "position": 0.0,
  "createdAt": "2026-07-31T12:00:00Z",
  "updatedAt": "2026-07-31T12:00:00Z"
}
```

### Response DTO: `TaskListResponse`

```java
public record TaskListResponse(
    UUID id,
    String name,
    boolean isInbox,
    double position,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
)
```

Fields included: all non-sensitive, non-internal fields. `userId` and `deletedAt` are excluded — `userId` is implicit from the auth context, and `deletedAt` is null for newly created lists.

### Error Responses

| Status | Condition | Body |
|--------|-----------|------|
| 401 | Missing/invalid `X-User-Id` | `{ "error": { "code": "UNAUTHORIZED", "message": "Authentication required" } }` |
| 422 | Blank name | `{ "error": { "code": "VALIDATION_ERROR", "message": "Validation failed", "details": [{ "field": "name", "message": "Name must not be blank" }] } }` |
| 422 | Name > 120 chars | `{ "error": { "code": "VALIDATION_ERROR", "message": "Validation failed", "details": [{ "field": "name", "message": "Name must not exceed 120 characters" }] } }` |
| 422 | Missing/malformed body | `{ "error": { "code": "VALIDATION_ERROR", "message": "Validation failed", "details": [{ "message": "Malformed request body" }] } }` |

### JSON Naming Convention
Jackson default camelCase — `isInbox`, `createdAt`, `updatedAt`. Matches the existing `TaskResponse` conventions.
