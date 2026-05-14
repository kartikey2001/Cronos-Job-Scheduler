package com.chronos.job;

import com.chronos.AbstractIntegrationTest;
import com.chronos.dto.AuthResponse;
import com.chronos.dto.JobResponse;
import com.chronos.repository.JobRepository;
import com.chronos.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@AutoConfigureMockMvc
class JobControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JobRepository jobRepository;
    @Autowired private UserRepository userRepository;

    private String token;

    private static final String JOB_JSON = """
            {
              "name": "Nightly Report",
              "webhookUrl": "https://example.com/hook",
              "scheduledAt": "2026-06-01T10:00:00",
              "maxRetries": 3,
              "retryDelayMs": 5000
            }
            """;

    @BeforeEach
    void setup() throws Exception {
        jobRepository.deleteAll();
        userRepository.deleteAll();
        token = registerAndGetToken("alice", "alice@example.com");
    }

    private String registerAndGetToken(String username, String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"password123\"}",
                                username, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).token();
    }

    private String createJobAndGetId() throws Exception {
        String body = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JOB_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, JobResponse.class).id().toString();
    }

    @Test
    void createJob_returns_201_with_pending_status() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JOB_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Nightly Report"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.webhookUrl").value("https://example.com/hook"));
    }

    @Test
    void createJob_returns_400_for_missing_required_fields() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"no name or url\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createJob_returns_401_without_token() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JOB_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getJob_returns_200() throws Exception {
        String jobId = createJobAndGetId();

        mockMvc.perform(get("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId))
                .andExpect(jsonPath("$.name").value("Nightly Report"));
    }

    @Test
    void getJob_returns_404_for_other_users_job() throws Exception {
        String jobId = createJobAndGetId();
        String otherToken = registerAndGetToken("bob", "bob@example.com");

        mockMvc.perform(get("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJob_returns_404_for_nonexistent_job() throws Exception {
        mockMvc.perform(get("/api/jobs/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listJobs_returns_paged_response() throws Exception {
        createJobAndGetId();
        createJobAndGetId();

        mockMvc.perform(get("/api/jobs?page=0&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void listJobs_only_returns_own_jobs() throws Exception {
        createJobAndGetId();
        String otherToken = registerAndGetToken("bob", "bob@example.com");

        mockMvc.perform(get("/api/jobs")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void updateJob_returns_200_with_updated_name() throws Exception {
        String jobId = createJobAndGetId();

        mockMvc.perform(put("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.webhookUrl").value("https://example.com/hook"));
    }

    @Test
    void updateJob_returns_404_for_other_users_job() throws Exception {
        String jobId = createJobAndGetId();
        String otherToken = registerAndGetToken("bob", "bob@example.com");

        mockMvc.perform(put("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelJob_returns_204() throws Exception {
        String jobId = createJobAndGetId();

        mockMvc.perform(delete("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Job still exists with CANCELLED status
        mockMvc.perform(get("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelJob_returns_400_when_already_cancelled() throws Exception {
        String jobId = createJobAndGetId();

        mockMvc.perform(delete("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateJob_returns_400_when_job_already_cancelled() throws Exception {
        String jobId = createJobAndGetId();

        mockMvc.perform(delete("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Too Late\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── DLQ endpoints ──────────────────────────────────────────────────────

    @Test
    void getDlqEntries_returns_200_and_empty_list_for_pending_job() throws Exception {
        String jobId = createJobAndGetId();

        mockMvc.perform(get("/api/jobs/" + jobId + "/dlq")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getDlqEntries_returns_404_for_unknown_job() throws Exception {
        mockMvc.perform(get("/api/jobs/" + UUID.randomUUID() + "/dlq")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void retryFromDlq_returns_400_when_job_is_not_dead() throws Exception {
        String jobId = createJobAndGetId();

        mockMvc.perform(post("/api/jobs/" + jobId + "/dlq/retry")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ── Request-ID tracing ─────────────────────────────────────────────────

    @Test
    void response_includes_x_request_id_header() throws Exception {
        mockMvc.perform(get("/api/jobs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    void response_echoes_provided_x_request_id() throws Exception {
        mockMvc.perform(get("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-ID", "my-trace-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "my-trace-id"));
    }
}
