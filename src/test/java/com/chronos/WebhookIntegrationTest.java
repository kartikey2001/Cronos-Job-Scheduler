package com.chronos;

import com.chronos.dto.AuthResponse;
import com.chronos.dto.JobResponse;
import com.chronos.repository.DlqRepository;
import com.chronos.repository.ExecutionLogRepository;
import com.chronos.repository.JobRepository;
import com.chronos.repository.UserRepository;
import com.chronos.service.WebhookExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WebhookIntegrationTest extends AbstractIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig()
                    .withUser("test@chronos.io", "test"))
            .withPerMethodLifecycle(true);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WebhookExecutionService webhookExecutionService;
    @Autowired private JobRepository jobRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ExecutionLogRepository executionLogRepository;
    @Autowired private DlqRepository dlqRepository;

    private String token;
    private UUID createdJobId;

    @BeforeEach
    void setup() throws Exception {
        dlqRepository.deleteAll();
        executionLogRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
        token = registerAndGetToken();
    }

    private String registerAndGetToken() throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).token();
    }

    private UUID createJob(String webhookUrl, int maxRetries) throws Exception {
        String payload = String.format("""
                {
                  "name": "Test Job",
                  "webhookUrl": "%s",
                  "scheduledAt": "2099-01-01T00:00:00",
                  "maxRetries": %d,
                  "retryDelayMs": 600000
                }
                """, webhookUrl, maxRetries);

        String body = mockMvc.perform(MockMvcRequestBuilders.post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, JobResponse.class).id();
    }

    @Test
    void webhook_success_sets_job_to_success_and_sends_email() throws Exception {
        wireMock.stubFor(WireMock.post("/hook").willReturn(WireMock.ok()));
        createdJobId = createJob("http://localhost:" + wireMock.getPort() + "/hook", 3);

        webhookExecutionService.execute(createdJobId);

        var job = jobRepository.findById(createdJobId).orElseThrow();
        assertThat(job.getStatus().name()).isEqualTo("SUCCESS");

        var logs = executionLogRepository.findByJobId(createdJobId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus().name()).isEqualTo("SUCCESS");

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        var messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages[0].getSubject()).contains("Test Job");

        wireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/hook")));
    }

    @Test
    void webhook_failure_with_no_retries_moves_to_dlq_and_sends_email() throws Exception {
        wireMock.stubFor(WireMock.post("/hook").willReturn(WireMock.serverError()));
        createdJobId = createJob("http://localhost:" + wireMock.getPort() + "/hook", 0);

        webhookExecutionService.execute(createdJobId);

        var job = jobRepository.findById(createdJobId).orElseThrow();
        assertThat(job.getStatus().name()).isEqualTo("DEAD");

        var dlqEntries = dlqRepository.findByJobId(createdJobId);
        assertThat(dlqEntries).hasSize(1);

        var logs = executionLogRepository.findByJobId(createdJobId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus().name()).isEqualTo("FAILED");

        assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
        var messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages[0].getSubject()).contains("failed permanently");
    }

    @Test
    void webhook_failure_with_retries_remaining_re_enqueues_job() throws Exception {
        wireMock.stubFor(WireMock.post("/hook").willReturn(WireMock.serverError()));
        createdJobId = createJob("http://localhost:" + wireMock.getPort() + "/hook", 2);

        webhookExecutionService.execute(createdJobId);

        var job = jobRepository.findById(createdJobId).orElseThrow();
        assertThat(job.getStatus().name()).isEqualTo("PENDING");
        assertThat(job.getRetryCount()).isEqualTo(1);

        var dlqEntries = dlqRepository.findByJobId(createdJobId);
        assertThat(dlqEntries).isEmpty();
    }

    @Test
    void dlq_retry_resets_dead_job_to_pending_and_clears_entries() throws Exception {
        wireMock.stubFor(WireMock.post("/hook").willReturn(WireMock.serverError()));
        createdJobId = createJob("http://localhost:" + wireMock.getPort() + "/hook", 0);

        webhookExecutionService.execute(createdJobId);
        assertThat(jobRepository.findById(createdJobId).orElseThrow().getStatus().name()).isEqualTo("DEAD");
        assertThat(dlqRepository.findByJobId(createdJobId)).hasSize(1);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/jobs/" + createdJobId + "/dlq/retry")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());

        var job = jobRepository.findById(createdJobId).orElseThrow();
        assertThat(job.getStatus().name()).isEqualTo("PENDING");
        assertThat(job.getRetryCount()).isEqualTo(0);
        assertThat(dlqRepository.findByJobId(createdJobId)).isEmpty();
    }
}
