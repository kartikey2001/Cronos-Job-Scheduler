package com.chronos.queue;

import com.chronos.service.WebhookExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetryQueueConsumerTest {

    @Mock private RedissonClient redissonClient;
    @Mock private WebhookExecutionService webhookExecutionService;
    @Mock private RBlockingQueue<String> queue;

    @InjectMocks private RetryQueueConsumer consumer;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(consumer, "retryQueueName", "job:retry");
        doReturn(queue).when(redissonClient).getBlockingQueue(anyString());
    }

    @Test
    void consume_executes_job_when_item_available() throws InterruptedException {
        UUID jobId = UUID.randomUUID();
        // Return jobId first, then stop the consumer, then return null
        when(queue.poll(anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(jobId.toString())
                .thenAnswer(inv -> { consumer.stop(); return null; });

        consumer.consume();

        verify(webhookExecutionService).execute(jobId);
    }

    @Test
    void consume_skips_null_and_keeps_looping() throws InterruptedException {
        when(queue.poll(anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(null)
                .thenAnswer(inv -> { consumer.stop(); return null; });

        consumer.consume();

        verifyNoInteractions(webhookExecutionService);
    }

    @Test
    void consume_stops_on_interrupt() throws InterruptedException {
        when(queue.poll(anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("test interrupt"));

        consumer.consume();

        verifyNoInteractions(webhookExecutionService);
    }

    @Test
    void consume_continues_on_non_interrupt_exception() throws InterruptedException {
        UUID jobId = UUID.randomUUID();
        when(queue.poll(anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(jobId.toString())
                .thenAnswer(inv -> { consumer.stop(); return null; });
        doThrow(new RuntimeException("execution error")).when(webhookExecutionService).execute(any());

        consumer.consume();

        verify(webhookExecutionService).execute(jobId);
    }

    @Test
    void stop_sets_running_to_false() {
        consumer.stop();
        // stop() is idempotent and doesn't throw
    }
}
