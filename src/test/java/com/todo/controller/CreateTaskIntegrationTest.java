package com.todo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.model.TaskList;
import com.todo.model.UserAccount;
import com.todo.repository.TaskListRepository;
import com.todo.repository.TaskRepository;
import com.todo.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreateTaskIntegrationTest {

    // MockMvc operates within the servlet context (/api), so the path excludes the context prefix.
    // Real HTTP clients hit /api/v1/tasks; MockMvc hits /v1/tasks.
    private static final String ENDPOINT = "/v1/tasks";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private TaskListRepository taskListRepository;
    @Autowired private TaskRepository taskRepository;

    private UserAccount user1;
    private UserAccount user2;
    private TaskList list1;
    private TaskList list2ForUser2;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        taskListRepository.deleteAll();
        userAccountRepository.deleteAll();

        user1 = userAccountRepository.save(new UserAccount("user1@test.com", "hashed1"));
        user2 = userAccountRepository.save(new UserAccount("user2@test.com", "hashed2"));

        list1 = taskListRepository.save(new TaskList(user1.getId(), "My List", false, 0));
        list2ForUser2 = taskListRepository.save(new TaskList(user2.getId(), "User2 List", false, 0));
    }

    // ── Helper ──────────────────────────────────────────

    private String toJson(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Buy groceries");
        body.put("listId", list1.getId().toString());
        body.put("notes", "Milk, eggs, bread");
        body.put("dueAt", "2026-08-15T10:00:00Z");
        body.put("priority", "med");
        return body;
    }

    // ── AC-1: Happy path — full task creation ───────────

    @Nested
    @DisplayName("AC-1: Create task with valid data")
    class AC1_HappyPath {

        @Test
        @DisplayName("201 with generated UUID, position=0, completedAt=null, version=0, full body")
        void createTask_happyPath() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.title").value("Buy groceries"))
                    .andExpect(jsonPath("$.listId").value(list1.getId().toString()))
                    .andExpect(jsonPath("$.notes").value("Milk, eggs, bread"))
                    .andExpect(jsonPath("$.dueAt").value(startsWith("2026-08-15T10:00:00")))
                    .andExpect(jsonPath("$.priority").value("med"))
                    .andExpect(jsonPath("$.position").value(0.0))
                    .andExpect(jsonPath("$.completedAt").value(nullValue()))
                    .andExpect(jsonPath("$.version").value(0))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                    .andExpect(header().string("Location", matchesPattern("/v1/tasks/[0-9a-f\\-]+")));
        }

        @Test
        @DisplayName("Second task in the same list gets position=1")
        void createTask_secondTask_positionIncremented() throws Exception {
            // First task → position 0
            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.position").value(0.0));

            // Second task → position 1
            Map<String, Object> body2 = validBody();
            body2.put("title", "Second task");
            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body2)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.position").value(1.0));
        }

        @Test
        @DisplayName("Minimal request — only title and listId, no optional fields")
        void createTask_minimalRequest() throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "Simple task");
            body.put("listId", list1.getId().toString());

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Simple task"))
                    .andExpect(jsonPath("$.notes").value(nullValue()))
                    .andExpect(jsonPath("$.dueAt").value(nullValue()))
                    .andExpect(jsonPath("$.priority").value("none"))
                    .andExpect(jsonPath("$.position").value(0.0))
                    .andExpect(jsonPath("$.completedAt").value(nullValue()))
                    .andExpect(jsonPath("$.version").value(0));
        }
    }

    // ── AC-2: Priority validation and defaulting ────────

    @Nested
    @DisplayName("AC-2: Priority defaults and validation")
    class AC2_Priority {

        @Test
        @DisplayName("Omitting priority defaults to 'none'")
        void createTask_noPriority_defaultsToNone() throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "No priority task");
            body.put("listId", list1.getId().toString());

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.priority").value("none"));
        }

        @Test
        @DisplayName("Providing priority='high' stores as 'high'")
        void createTask_highPriority() throws Exception {
            Map<String, Object> body = validBody();
            body.put("priority", "high");

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.priority").value("high"));
        }

        @Test
        @DisplayName("Invalid priority 'urgent' returns 422 VALIDATION_ERROR")
        void createTask_invalidPriority_returns422() throws Exception {
            Map<String, Object> body = validBody();
            body.put("priority", "urgent");

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    // ── AC-3: Notes length validation ───────────────────

    @Nested
    @DisplayName("AC-3: Notes length validation")
    class AC3_Notes {

        @Test
        @DisplayName("Notes at exactly 10000 chars succeeds (201)")
        void createTask_notesExactly10000_succeeds() throws Exception {
            Map<String, Object> body = validBody();
            body.put("notes", "a".repeat(10000));

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.notes").isNotEmpty());
        }

        @Test
        @DisplayName("Notes exceeding 10000 chars returns 422")
        void createTask_notesExceed10000_returns422() throws Exception {
            Map<String, Object> body = validBody();
            body.put("notes", "a".repeat(10001));

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[?(@.field=='notes')]").exists());
        }
    }

    // ── AC-4: Title validation ──────────────────────────

    @Nested
    @DisplayName("AC-4: Title validation")
    class AC4_Title {

        @Test
        @DisplayName("Empty title returns 422")
        void createTask_emptyTitle_returns422() throws Exception {
            Map<String, Object> body = validBody();
            body.put("title", "");

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Whitespace-only title returns 422")
        void createTask_whitespaceTitle_returns422() throws Exception {
            Map<String, Object> body = validBody();
            body.put("title", "   ");

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Title exceeding 500 chars returns 422")
        void createTask_titleExceed500_returns422() throws Exception {
            Map<String, Object> body = validBody();
            body.put("title", "a".repeat(501));

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Title at exactly 500 chars succeeds (201)")
        void createTask_titleExactly500_succeeds() throws Exception {
            Map<String, Object> body = validBody();
            body.put("title", "a".repeat(500));

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("a".repeat(500)));
        }

        @Test
        @DisplayName("Missing title returns 422")
        void createTask_missingTitle_returns422() throws Exception {
            Map<String, Object> body = validBody();
            body.remove("title");

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    // ── AC-5: dueAt stored as UTC, returned as ISO-8601 ─

    @Nested
    @DisplayName("AC-5: Due date as UTC ISO-8601")
    class AC5_DueAt {

        @Test
        @DisplayName("dueAt stored and returned as ISO-8601 OffsetDateTime")
        void createTask_dueAt_storedAsUtc() throws Exception {
            Map<String, Object> body = validBody();
            body.put("dueAt", "2026-08-15T10:00:00Z");

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.dueAt").value(startsWith("2026-08-15T10:00:00")));
        }

        @Test
        @DisplayName("dueAt with timezone offset is accepted")
        void createTask_dueAt_withOffset() throws Exception {
            Map<String, Object> body = validBody();
            body.put("dueAt", "2026-08-15T15:30:00+05:30");

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.dueAt").isNotEmpty());
        }
    }

    // ── AC-6: List not found / not owned → 404 ─────────

    @Nested
    @DisplayName("AC-6: List ownership — 404 not 403")
    class AC6_ListOwnership {

        @Test
        @DisplayName("Non-existent listId returns 404")
        void createTask_nonExistentList_returns404() throws Exception {
            Map<String, Object> body = validBody();
            body.put("listId", UUID.randomUUID().toString());

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.error.message").value("List not found"));
        }

        @Test
        @DisplayName("listId belonging to another user returns 404 (not 403)")
        void createTask_otherUsersLlist_returns404() throws Exception {
            // user1 tries to create a task in user2's list
            Map<String, Object> body = validBody();
            body.put("listId", list2ForUser2.getId().toString());

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.error.message").value("List not found"));
        }
    }

    // ── AC-7: X-Request-Id propagation ──────────────────

    @Nested
    @DisplayName("AC-7: X-Request-Id header propagation")
    class AC7_RequestId {

        @Test
        @DisplayName("X-Request-Id from request is echoed in response")
        void createTask_requestIdPropagated() throws Exception {
            String requestId = UUID.randomUUID().toString();

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .header(REQUEST_ID_HEADER, requestId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(REQUEST_ID_HEADER, requestId));
        }

        @Test
        @DisplayName("Missing X-Request-Id results in auto-generated value in response")
        void createTask_noRequestId_autoGenerated() throws Exception {
            MvcResult result = mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists(REQUEST_ID_HEADER))
                    .andReturn();

            String generatedId = result.getResponse().getHeader(REQUEST_ID_HEADER);
            // Verify it is a valid UUID
            UUID.fromString(generatedId);
        }
    }
}
