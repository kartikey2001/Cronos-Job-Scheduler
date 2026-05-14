package com.chronos.worker;

import com.chronos.service.WebhookExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Slf4j
public class WebhookJobExecutor implements Job {

    @Autowired
    private WebhookExecutionService webhookExecutionService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String jobId = context.getMergedJobDataMap().getString("jobId");
        log.debug("Quartz firing for job {}", jobId);
        try {
            webhookExecutionService.execute(UUID.fromString(jobId));
        } catch (Exception e) {
            throw new JobExecutionException("Webhook execution failed for job " + jobId, e);
        }
    }
}
