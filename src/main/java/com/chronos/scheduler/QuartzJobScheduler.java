package com.chronos.scheduler;

import com.chronos.domain.Job;
import com.chronos.worker.WebhookJobExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuartzJobScheduler {

    private static final String GROUP = "webhook-jobs";
    private static final String TRIGGER_GROUP = "webhook-triggers";

    private final Scheduler scheduler;

    public void schedule(Job job) {
        JobDetail jobDetail = JobBuilder.newJob(WebhookJobExecutor.class)
                .withIdentity(job.getId().toString(), GROUP)
                .usingJobData("jobId", job.getId().toString())
                .storeDurably()
                .build();

        Trigger trigger = buildTrigger(job);

        try {
            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Scheduled Quartz job for Job {}", job.getId());
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to schedule job " + job.getId(), e);
        }
    }

    public void unschedule(UUID jobId) {
        try {
            scheduler.deleteJob(new JobKey(jobId.toString(), GROUP));
            log.info("Unscheduled Quartz job for Job {}", jobId);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to unschedule job " + jobId, e);
        }
    }

    private Trigger buildTrigger(Job job) {
        TriggerBuilder<Trigger> builder = TriggerBuilder.newTrigger()
                .withIdentity(job.getId().toString(), TRIGGER_GROUP);

        if (job.getCronExpression() != null && !job.getCronExpression().isBlank()) {
            return builder
                    .withSchedule(CronScheduleBuilder.cronSchedule(job.getCronExpression()))
                    .build();
        }

        Date fireAt = Date.from(job.getScheduledAt().atZone(ZoneId.systemDefault()).toInstant());
        return builder
                .startAt(fireAt)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .build();
    }
}
