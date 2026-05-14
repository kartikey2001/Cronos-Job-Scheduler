package com.chronos.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "execution_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public ExecutionLog(UUID jobId, UUID executionId, int attempt, LocalDateTime startedAt) {
        this.jobId = jobId;
        this.executionId = executionId;
        this.attempt = attempt;
        this.startedAt = startedAt;
        this.status = ExecutionStatus.RUNNING;
    }

    public void complete(ExecutionStatus newStatus, String output, String errorMessage) {
        this.status = newStatus;
        this.output = output;
        this.errorMessage = errorMessage;
        this.finishedAt = LocalDateTime.now();
    }
}
