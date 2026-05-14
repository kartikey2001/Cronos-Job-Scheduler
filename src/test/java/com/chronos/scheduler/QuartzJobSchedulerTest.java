package com.chronos.scheduler;

import com.chronos.domain.Job;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuartzJobSchedulerTest {

    @Mock private Scheduler scheduler;
    @InjectMocks private QuartzJobScheduler quartzJobScheduler;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(1);

    private Job jobWithId(String cronExpression) {
        Job job = new Job(USER_ID, "Job", null, "https://example.com/hook", cronExpression, FUTURE, 3, 5000L);
        ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
        return job;
    }

    @Test
    void schedule_one_time_job() throws SchedulerException {
        quartzJobScheduler.schedule(jobWithId(null));

        verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void schedule_cron_job() throws SchedulerException {
        quartzJobScheduler.schedule(jobWithId("0 0 2 * * ?"));

        verify(scheduler).scheduleJob(any(JobDetail.class), any(CronTrigger.class));
    }

    @Test
    void schedule_wraps_scheduler_exception() throws SchedulerException {
        Job job = jobWithId(null);
        doThrow(SchedulerException.class).when(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));

        assertThatThrownBy(() -> quartzJobScheduler.schedule(job))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(job.getId().toString());
    }

    @Test
    void unschedule_deletes_quartz_job() throws SchedulerException {
        UUID jobId = UUID.randomUUID();

        quartzJobScheduler.unschedule(jobId);

        verify(scheduler).deleteJob(any(JobKey.class));
    }

    @Test
    void unschedule_wraps_scheduler_exception() throws SchedulerException {
        UUID jobId = UUID.randomUUID();
        doThrow(SchedulerException.class).when(scheduler).deleteJob(any(JobKey.class));

        assertThatThrownBy(() -> quartzJobScheduler.unschedule(jobId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(jobId.toString());
    }
}
