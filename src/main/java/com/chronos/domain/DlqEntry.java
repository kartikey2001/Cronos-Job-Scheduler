package com.chronos.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DlqEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "moved_at", nullable = false)
    private LocalDateTime movedAt;

    public DlqEntry(UUID jobId, String failureReason) {
        this.jobId = jobId;
        this.failureReason = failureReason;
        this.movedAt = LocalDateTime.now();
    }
}
