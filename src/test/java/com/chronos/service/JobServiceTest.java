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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private QuartzJobScheduler quartzJobScheduler;
    @InjectMocks private JobService jobService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID JOB_ID  = UUID.randomUUID();
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(1);

    private Job pendingJob() {
        return new Job(USER_ID, "My Job", null, "https://example.com/hook",
                null, FUTURE, 3, 5000L);
    }

    @Test
    void createJob_success_returns_response() {
        CreateJobRequest req = new CreateJobRequest(
                "My Job", null, "https://example.com/hook", null, FUTURE, 3, 5000L);
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        JobResponse resp = jobService.createJob(USER_ID, req);

        assertThat(resp.name()).isEqualTo("My Job");
        assertThat(resp.status()).isEqualTo("PENDING");
        assertThat(resp.userId()).isEqualTo(USER_ID);
        verify(jobRepository).save(any(Job.class));
    }

    @Test
    void createJob_applies_defaults_when_optional_fields_null() {
        CreateJobRequest req = new CreateJobRequest(
                "My Job", null, "https://example.com/hook", null, FUTURE, null, null);
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        JobResponse resp = jobService.createJob(USER_ID, req);

        assertThat(resp.maxRetries()).isEqualTo(3);
        assertThat(resp.retryDelayMs()).isEqualTo(5000L);
    }

    @Test
    void getJob_success_returns_response() {
        Job job = pendingJob();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));

        JobResponse resp = jobService.getJob(USER_ID, JOB_ID);

        assertThat(resp.name()).isEqualTo("My Job");
    }

    @Test
    void getJob_throws_not_found_for_unknown_job() {
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getJob(USER_ID, JOB_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listJobs_returns_paged_response() {
        Job job = pendingJob();
        Pageable pageable = PageRequest.of(0, 20);
        when(jobRepository.findAllByUserId(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(job), pageable, 1));

        PageResponse<JobResponse> resp = jobService.listJobs(USER_ID, pageable);

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.totalElements()).isEqualTo(1);
        assertThat(resp.page()).isEqualTo(0);
    }

    @Test
    void updateJob_success_applies_non_null_fields() {
        Job job = pendingJob();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        UpdateJobRequest req = new UpdateJobRequest("New Name", null, null, null, null, null, null);
        JobResponse resp = jobService.updateJob(USER_ID, JOB_ID, req);

        assertThat(resp.name()).isEqualTo("New Name");
    }

    @Test
    void updateJob_throws_not_found_for_unknown_job() {
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.updateJob(USER_ID, JOB_ID,
                new UpdateJobRequest(null, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateJob_throws_bad_request_when_job_not_pending() {
        Job job = pendingJob();
        job.cancel(); // sets status to CANCELLED
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.updateJob(USER_ID, JOB_ID,
                new UpdateJobRequest("x", null, null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void updateJob_reschedules_quartz_when_scheduled_at_changes() {
        Job job = pendingJob();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        UpdateJobRequest req = new UpdateJobRequest(null, null, null, null, FUTURE.plusDays(1), null, null);
        jobService.updateJob(USER_ID, JOB_ID, req);

        verify(quartzJobScheduler).unschedule(JOB_ID);
        verify(quartzJobScheduler).schedule(any());
    }

    @Test
    void updateJob_does_not_reschedule_when_only_name_changes() {
        Job job = pendingJob();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        UpdateJobRequest req = new UpdateJobRequest("New Name", null, null, null, null, null, null);
        jobService.updateJob(USER_ID, JOB_ID, req);

        verifyNoInteractions(quartzJobScheduler);
    }

    @Test
    void cancelJob_success_changes_status_to_cancelled() {
        Job job = pendingJob();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        jobService.cancelJob(USER_ID, JOB_ID);

        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        verify(jobRepository).save(job);
    }

    @Test
    void cancelJob_throws_not_found_for_unknown_job() {
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.cancelJob(USER_ID, JOB_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelJob_throws_bad_request_when_job_not_pending() {
        Job job = pendingJob();
        job.cancel();
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.cancelJob(USER_ID, JOB_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");
    }
}
