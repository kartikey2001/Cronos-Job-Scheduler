package com.chronos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;

public record CreateJobRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        String description,

        @NotBlank(message = "Webhook URL is required")
        @URL(message = "Webhook URL must be a valid URL")
        String webhookUrl,

        String cronExpression,

        @NotNull(message = "Scheduled time is required")
        LocalDateTime scheduledAt,

        Integer maxRetries,

        Long retryDelayMs
) {}
