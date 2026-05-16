package com.chronos.service;

import com.chronos.domain.*;
import com.chronos.notification.NotificationService;
import com.chronos.repository.ExecutionLogRepository;
import com.chronos.repository.JobRepository;
import com.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Owns all transactional DB operations for webhook execution so that
 * WebhookExecutionService can hold no transaction across the HTTP call.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WebhookExecutionStore {

    private final JobRepository jobRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final UserRepository userRepository;
    private final RetryService retryService;
    private final NotificationService notificationService;

    /**
     * Marks job RUNNING and creates an ExecutionLog row.
     * Returns null if the job is not found or not in PENDING state.
     */
    public ExecutionPrep beginExecution(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.PENDING) {
            return null;
        }

        int attempt = job.getRetryCount() + 1;
        job.updateStatus(JobStatus.RUNNING);
        jobRepository.save(job);

        ExecutionLog log = new ExecutionLog(job.getId(), UUID.randomUUID(), attempt, LocalDateTime.now());
        ExecutionLog saved = executionLogRepository.save(log);

        return new ExecutionPrep(saved.getId(), job.getWebhookUrl(), job.getUserId(), job.getName());
    }

    /** Marks job SUCCESS (or resets to PENDING for cron), completes the log, and sends the success email. */
    public void finalizeSuccess(UUID jobId, UUID execLogId, String responseBody) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        boolean isCron = job.getCronExpression() != null && !job.getCronExpression().isBlank();
        job.updateStatus(isCron ? JobStatus.PENDING : JobStatus.SUCCESS);
        job.resetRetryCount();
        jobRepository.save(job);

        ExecutionLog log = executionLogRepository.findById(execLogId).orElseThrow();
        log.complete(ExecutionStatus.SUCCESS, responseBody, null);
        executionLogRepository.save(log);

        if (!isCron) {
            userRepository.findById(job.getUserId()).ifPresent(user ->
                    notificationService.sendJobSuccess(user.getEmail(), job.getName()));
        }
    }

    /** Completes the log with FAILED and delegates to RetryService. */
    public void finalizeFailure(UUID jobId, UUID execLogId, String error) {
        Job job = jobRepository.findById(jobId).orElseThrow();

        ExecutionLog log = executionLogRepository.findById(execLogId).orElseThrow();
        log.complete(ExecutionStatus.FAILED, null, error);
        executionLogRepository.save(log);

        retryService.handleFailure(job, error);
    }

    public record ExecutionPrep(UUID execLogId, String webhookUrl, UUID userId, String jobName) {}
}
