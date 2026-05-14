package com.chronos.service;

import com.chronos.domain.Job;
import com.chronos.domain.JobStatus;
import com.chronos.dto.CreateJobRequest;
import com.chronos.dto.JobResponse;
import com.chronos.dto.PageResponse;
import com.chronos.dto.UpdateJobRequest;
import com.chronos.exception.BadRequestException;
import com.chronos.exception.ResourceNotFoundException;
import com.chronos.repository.JobRepository;
import com.chronos.scheduler.QuartzJobScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final QuartzJobScheduler quartzJobScheduler;

    @Transactional
    public JobResponse createJob(UUID userId, CreateJobRequest req) {
        Job job = new Job(
                userId,
                req.name(),
                req.description(),
                req.webhookUrl(),
                req.cronExpression(),
                req.scheduledAt(),
                req.maxRetries() != null ? req.maxRetries() : 3,
                req.retryDelayMs() != null ? req.retryDelayMs() : 5000L
        );
        Job saved = jobRepository.save(job);
        quartzJobScheduler.schedule(saved);
        return JobResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID userId, UUID jobId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .map(JobResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> listJobs(UUID userId, Pageable pageable) {
        return PageResponse.from(
                jobRepository.findAllByUserId(userId, pageable).map(JobResponse::from)
        );
    }

    @Transactional
    public JobResponse updateJob(UUID userId, UUID jobId, UpdateJobRequest req) {
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        if (job.getStatus() != JobStatus.PENDING) {
            throw new BadRequestException("Only PENDING jobs can be updated");
        }
        if (req.name() != null) job.setName(req.name());
        if (req.description() != null) job.setDescription(req.description());
        if (req.webhookUrl() != null) job.setWebhookUrl(req.webhookUrl());
        if (req.cronExpression() != null) job.setCronExpression(req.cronExpression());
        if (req.scheduledAt() != null) job.setScheduledAt(req.scheduledAt());
        if (req.maxRetries() != null) job.setMaxRetries(req.maxRetries());
        if (req.retryDelayMs() != null) job.setRetryDelayMs(req.retryDelayMs());
        Job saved = jobRepository.save(job);

        if (req.scheduledAt() != null || req.cronExpression() != null) {
            quartzJobScheduler.unschedule(jobId);
            quartzJobScheduler.schedule(saved);
        }

        return JobResponse.from(saved);
    }

    @Transactional
    public void cancelJob(UUID userId, UUID jobId) {
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        if (job.getStatus() != JobStatus.PENDING) {
            throw new BadRequestException("Only PENDING jobs can be cancelled");
        }
        job.cancel();
        jobRepository.save(job);
        quartzJobScheduler.unschedule(jobId);
    }
}
