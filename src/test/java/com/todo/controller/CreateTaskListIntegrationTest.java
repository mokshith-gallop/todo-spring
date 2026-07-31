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

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreateTaskListIntegrationTest {

    private static final String ENDPOINT = "/v1/lists";
    private static final String USER_ID_HEADER = "X-User-Id";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private TaskListRepository taskListRepository;
    @Autowired private TaskRepository taskRepository;

    private UserAccount user1;
    private UserAccount user2;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        taskListRepository.deleteAll();
        userAccountRepository.deleteAll();

        user1 = userAccountRepository.save(new UserAccount("listuser1@test.com", "hashed1"));
        user2 = userAccountRepository.save(new UserAccount("listuser2@test.com", "hashed2"));
    }

    // ── Helper ──────────────────────────────────────────

    private String toJson(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Shopping");
        return body;
    }

    // ── AC-1: Happy path — create list with valid data ──

    @Nested
    @DisplayName("AC-1: Create list with valid data")
    class AC1_HappyPath {

        @Test
        @DisplayName("201 with generated UUID, name, isInbox=false, position=0, timestamps")
        void createList_happyPath() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.name").value("Shopping"))
                    .andExpect(jsonPath("$.isInbox").value(false))
                    .andExpect(jsonPath("$.position").value(0.0))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                    .andExpect(header().string("Location", matchesPattern("/v1/lists/[0-9a-f\\-]+")));
        }

        @Test
        @DisplayName("Second list gets position=1")
        void createList_secondList_positionIncremented() throws Exception {
            // First list → position 0
            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.position").value(0.0));

            // Second list → position 1
            Map<String, Object> body2 = new HashMap<>();
            body2.put("name", "Work");
            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body2)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.position").value(1.0));
        }

        @Test
        @DisplayName("Name with leading/trailing whitespace is stored trimmed")
        void createList_nameWithWhitespace_isTrimmed() throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("name", "  Trimmed Name  ");

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Trimmed Name"));
        }

        @Test
        @DisplayName("Name at exactly 120 characters succeeds (201)")
        void createList_nameExactly120_succeeds() throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("name", "a".repeat(120));

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("a".repeat(120)));
        }
    }

    // ── AC-2: Blank/whitespace name → 422 ───────────────

    @Nested
    @DisplayName("AC-2: Blank or whitespace-only name returns 422")
    class AC2_BlankName {

        @Test
        @DisplayName("Empty name returns 422 VALIDATION_ERROR")
        void createList_emptyName_returns422() throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("name", "");

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[?(@.field=='name')]").exists());
        }

        @Test
        @DisplayName("Whitespace-only name returns 422 VALIDATION_ERROR")
        void createList_whitespaceName_returns422() throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("name", "   ");

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[?(@.field=='name')]").exists());
        }

        @Test
        @DisplayName("Null / missing name returns 422 VALIDATION_ERROR")
        void createList_nullName_returns422() throws Exception {
            // Send empty JSON object — name field is absent
            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    // ── AC-3: Name too long → 422 ──────────────────────

    @Nested
    @DisplayName("AC-3: Name exceeding 120 characters returns 422")
    class AC3_NameTooLong {

        @Test
        @DisplayName("Name with 121 characters returns 422 VALIDATION_ERROR")
        void createList_name121Chars_returns422() throws Exception {
            Map<String, Object> body = new HashMap<>();
            body.put("name", "a".repeat(121));

            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[?(@.field=='name')]").exists());
        }
    }

    // ── AC-4: Tenant isolation ─────────────────────────

    @Nested
    @DisplayName("AC-4: List is scoped to user_id — tenant isolation")
    class AC4_TenantIsolation {

        @Test
        @DisplayName("User1's list is invisible to user2's position query")
        void createList_tenantIsolation_positionQuery() throws Exception {
            // User1 creates a list
            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user1.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.position").value(0.0));

            // User2's max position should still be -1 (no lists)
            double user2MaxPosition = taskListRepository.findMaxPositionByUserId(user2.getId());
            assertEquals(-1.0, user2MaxPosition,
                    "User2 should not see User1's lists — max position should be -1");

            // User2 creates their own list — position starts at 0
            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, user2.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.position").value(0.0));
        }
    }

    // ── AC-5: Unauthenticated → 401 ────────────────────

    @Nested
    @DisplayName("AC-5: Unauthenticated requests return 401")
    class AC5_Unauthenticated {

        @Test
        @DisplayName("Missing X-User-Id header returns 401 UNAUTHORIZED")
        void createList_noUserIdHeader_returns401() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.error.message").value("Authentication required"));
        }

        @Test
        @DisplayName("Invalid (non-UUID) X-User-Id header returns 401 UNAUTHORIZED")
        void createList_invalidUserId_returns401() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .header(USER_ID_HEADER, "not-a-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.error.message").value("Authentication required"));
        }
    }
}
