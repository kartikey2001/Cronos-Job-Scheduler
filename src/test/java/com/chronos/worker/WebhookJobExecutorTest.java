package com.chronos.worker;

import com.chronos.service.WebhookExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookJobExecutorTest {

    @Mock private WebhookExecutionService webhookExecutionService;
    @InjectMocks private WebhookJobExecutor executor;

    @Test
    void execute_delegates_to_service() throws JobExecutionException {
        UUID jobId = UUID.randomUUID();
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("jobId", jobId.toString());

        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(dataMap);

        executor.execute(context);

        verify(webhookExecutionService).execute(jobId);
    }

    @Test
    void execute_wraps_exception_in_job_execution_exception() {
        UUID jobId = UUID.randomUUID();
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("jobId", jobId.toString());

        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(dataMap);
        doThrow(new RuntimeException("boom")).when(webhookExecutionService).execute(jobId);

        assertThatThrownBy(() -> executor.execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining(jobId.toString());
    }
}
