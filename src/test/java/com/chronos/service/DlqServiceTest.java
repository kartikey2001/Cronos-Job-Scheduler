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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlqServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private DlqRepository dlqRepository;
    @Mock private QuartzJobScheduler quartzJobScheduler;

    @InjectMocks private DlqService dlqService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID JOB_ID  = UUID.randomUUID();
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(1);

    private Job deadJob() {
        Job job = new Job(USER_ID, "My Job", null, "https://example.com/hook", null, FUTURE, 3, 5000L);
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        job.updateStatus(JobStatus.DEAD);
        return job;
    }

    // ── getDlqEntries ───────────────────────────────────────────────────────

    @Test
    void getDlqEntries_returns_mapped_entries() {
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(deadJob()));
        DlqEntry entry = new DlqEntry(JOB_ID, "timeout");
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        when(dlqRepository.findByJobId(JOB_ID)).thenReturn(List.of(entry));

        List<DlqEntryResponse> result = dlqService.getDlqEntries(USER_ID, JOB_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).failureReason()).isEqualTo("timeout");
        assertThat(result.get(0).jobId()).isEqualTo(JOB_ID);
    }

    @Test
    void getDlqEntries_throws_not_found_when_job_not_owned() {
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dlqService.getDlqEntries(USER_ID, JOB_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── retryFromDlq ────────────────────────────────────────────────────────

    @Test
    void retryFromDlq_resets_job_and_reschedules() {
        Job job = deadJob();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(dlqRepository.findByJobId(JOB_ID)).thenReturn(List.of());
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        dlqService.retryFromDlq(USER_ID, JOB_ID);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getRetryCount()).isEqualTo(0);
        verify(jobRepository).save(job);
        verify(quartzJobScheduler).unschedule(JOB_ID);
        verify(quartzJobScheduler).schedule(job);
    }

    @Test
    void retryFromDlq_deletes_dlq_entries_before_rescheduling() {
        Job job = deadJob();
        DlqEntry entry = new DlqEntry(JOB_ID, "error");
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(dlqRepository.findByJobId(JOB_ID)).thenReturn(List.of(entry));
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        dlqService.retryFromDlq(USER_ID, JOB_ID);

        verify(dlqRepository).deleteAll(List.of(entry));
    }

    @Test
    void retryFromDlq_throws_bad_request_when_job_not_dead() {
        Job job = new Job(USER_ID, "My Job", null, "https://example.com/hook", null, FUTURE, 3, 5000L);
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> dlqService.retryFromDlq(USER_ID, JOB_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DEAD");
    }

    @Test
    void retryFromDlq_throws_not_found_when_job_not_owned() {
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dlqService.retryFromDlq(USER_ID, JOB_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
