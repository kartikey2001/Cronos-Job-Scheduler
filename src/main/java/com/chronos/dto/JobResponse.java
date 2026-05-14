package com.chronos.dto;

import com.chronos.domain.Job;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobResponse(
        UUID id,
        UUID userId,
        String name,
        String description,
        String webhookUrl,
        String cronExpression,
        LocalDateTime scheduledAt,
        String status,
        int maxRetries,
        int retryCount,
        long retryDelayMs,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getUserId(),
                job.getName(),
                job.getDescription(),
                job.getWebhookUrl(),
                job.getCronExpression(),
                job.getScheduledAt(),
                job.getStatus().name(),
                job.getMaxRetries(),
                job.getRetryCount(),
                job.getRetryDelayMs(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
