package com.chronos.service;

import com.chronos.domain.DlqEntry;
import com.chronos.domain.Job;
import com.chronos.domain.JobStatus;
import com.chronos.dto.DlqEntryResponse;
import com.chronos.exception.BadRequestException;
import com.chronos.exception.ResourceNotFoundException;
import com.chronos.repository.DlqRepository;
import com.chronos.repository.JobRepository;
import com.chronos.scheduler.QuartzJobScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DlqService {

    private final JobRepository jobRepository;
    private final DlqRepository dlqRepository;
    private final QuartzJobScheduler quartzJobScheduler;

    @Transactional(readOnly = true)
    public List<DlqEntryResponse> getDlqEntries(UUID userId, UUID jobId) {
        jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        return dlqRepository.findByJobId(jobId).stream()
                .map(DlqEntryResponse::from)
                .toList();
    }

    @Transactional
    public void retryFromDlq(UUID userId, UUID jobId) {
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        if (job.getStatus() != JobStatus.DEAD) {
            throw new BadRequestException("Only DEAD jobs can be retried from DLQ");
        }
        List<DlqEntry> entries = dlqRepository.findByJobId(jobId);
        dlqRepository.deleteAll(entries);
        job.resetForRetry();
        jobRepository.save(job);
        quartzJobScheduler.unschedule(jobId);
        quartzJobScheduler.schedule(job);
    }
}
