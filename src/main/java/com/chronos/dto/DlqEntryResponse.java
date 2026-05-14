package com.chronos.dto;

import com.chronos.domain.DlqEntry;

import java.time.LocalDateTime;
import java.util.UUID;

public record DlqEntryResponse(UUID id, UUID jobId, String failureReason, LocalDateTime movedAt) {

    public static DlqEntryResponse from(DlqEntry entry) {
        return new DlqEntryResponse(entry.getId(), entry.getJobId(), entry.getFailureReason(), entry.getMovedAt());
    }
}
