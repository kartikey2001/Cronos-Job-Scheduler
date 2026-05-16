package com.chronos.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String description;

    @Setter
    @Column(name = "webhook_url", nullable = false, columnDefinition = "TEXT")
    private String webhookUrl;

    @Setter
    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Setter
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Setter
    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Setter
    @Column(name = "retry_delay_ms", nullable = false)
    private long retryDelayMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Job(UUID userId, String name, String description, String webhookUrl,
               String cronExpression, LocalDateTime scheduledAt, int maxRetries, long retryDelayMs) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.webhookUrl = webhookUrl;
        this.cronExpression = cronExpression;
        this.scheduledAt = scheduledAt;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.status = JobStatus.PENDING;
        this.retryCount = 0;
    }

    public void cancel() {
        this.status = JobStatus.CANCELLED;
    }

    public void updateStatus(JobStatus newStatus) {
        this.status = newStatus;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public void resetForRetry() {
        this.status = JobStatus.PENDING;
        this.retryCount = 0;
    }

    public void resetRetryCount() {
        this.retryCount = 0;
    }
}
