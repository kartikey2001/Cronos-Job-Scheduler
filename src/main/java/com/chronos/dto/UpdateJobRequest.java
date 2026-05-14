package com.chronos.dto;

import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;

public record UpdateJobRequest(
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        String description,

        @URL(message = "Webhook URL must be a valid URL")
        String webhookUrl,

        String cronExpression,

        LocalDateTime scheduledAt,

        Integer maxRetries,

        Long retryDelayMs
) {}
