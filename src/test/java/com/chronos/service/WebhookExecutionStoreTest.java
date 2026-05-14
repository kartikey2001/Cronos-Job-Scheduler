package com.chronos.service;

import com.chronos.domain.*;
import com.chronos.notification.NotificationService;
import com.chronos.repository.ExecutionLogRepository;
import com.chronos.repository.JobRepository;
import com.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookExecutionStoreTest {

    @Mock private JobRepository jobRepository;
    @Mock private ExecutionLogRepository executionLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private RetryService retryService;
    @Mock private NotificationService notificationService;

    @InjectMocks private WebhookExecutionStore store;

    private static final UUID JOB_ID      = UUID.randomUUID();
    private static final UUID USER_ID     = UUID.randomUUID();
    private static final UUID EXEC_LOG_ID = UUID.randomUUID();
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(1);

    private Job runningJob() {
        Job job = new Job(USER_ID, "My Job", null, "https://example.com/hook", null, FUTURE, 3, 5000L);
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        job.updateStatus(JobStatus.RUNNING);
        return job;
    }

    private ExecutionLog savedLog() {
        ExecutionLog log = new ExecutionLog(JOB_ID, UUID.randomUUID(), 1, LocalDateTime.now());
        ReflectionTestUtils.setField(log, "id", EXEC_LOG_ID);
        return log;
    }

    // ── beginExecution ──────────────────────────────────────────────────────

    @Test
    void beginExecution_returns_null_when_job_not_found() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());
        assertThat(store.beginExecution(JOB_ID)).isNull();
    }

    @Test
    void beginExecution_returns_null_when_job_not_pending() {
        Job job = new Job(USER_ID, "J", null, "https://x.com", null, FUTURE, 3, 5000L);
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        job.cancel();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        assertThat(store.beginExecution(JOB_ID)).isNull();
    }

    @Test
    void beginExecution_sets_running_and_returns_prep() {
        Job job = new Job(USER_ID, "My Job", null, "https://example.com/hook", null, FUTURE, 3, 5000L);
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(executionLogRepository.save(any())).thenAnswer(invocation -> {
            ExecutionLog log = invocation.getArgument(0);
            ReflectionTestUtils.setField(log, "id", EXEC_LOG_ID);
            return log;
        });

        var prep = store.beginExecution(JOB_ID);

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(prep).isNotNull();
        assertThat(prep.execLogId()).isEqualTo(EXEC_LOG_ID);
        assertThat(prep.webhookUrl()).isEqualTo("https://example.com/hook");
    }

    // ── finalizeSuccess ──────────────────────────────────────────────────────

    @Test
    void finalizeSuccess_updates_job_log_and_sends_email() {
        Job job = runningJob();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ExecutionLog log = savedLog();
        when(executionLogRepository.findById(EXEC_LOG_ID)).thenReturn(Optional.of(log));
        when(executionLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User user = new User("alice", "alice@example.com", "hash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        store.finalizeSuccess(JOB_ID, EXEC_LOG_ID, "webhook body");

        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(log.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(log.getOutput()).isEqualTo("webhook body");
        verify(notificationService).sendJobSuccess("alice@example.com", "My Job");
    }

    // ── finalizeFailure ──────────────────────────────────────────────────────

    @Test
    void finalizeFailure_completes_log_and_delegates_to_retry_service() {
        Job job = runningJob();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        ExecutionLog log = savedLog();
        when(executionLogRepository.findById(EXEC_LOG_ID)).thenReturn(Optional.of(log));
        when(executionLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        store.finalizeFailure(JOB_ID, EXEC_LOG_ID, "HTTP 503");

        assertThat(log.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(log.getErrorMessage()).isEqualTo("HTTP 503");
        verify(retryService).handleFailure(job, "HTTP 503");
    }
}
