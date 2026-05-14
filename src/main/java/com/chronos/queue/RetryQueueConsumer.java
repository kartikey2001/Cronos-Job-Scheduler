package com.chronos.queue;

import com.chronos.service.WebhookExecutionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetryQueueConsumer {

    private final RedissonClient redissonClient;
    private final WebhookExecutionService webhookExecutionService;

    @Value("${app.retry.queue:job:retry}")
    private String retryQueueName;

    private volatile boolean running = true;

    @PostConstruct
    public void startConsumer() {
        Thread.ofVirtual().name("retry-consumer").start(this::consume);
        log.info("Retry queue consumer started on queue '{}'", retryQueueName);
    }

    @PreDestroy
    public void stop() {
        running = false;
        log.info("Retry queue consumer stopping");
    }

    void consume() {
        RBlockingQueue<String> queue = redissonClient.getBlockingQueue(retryQueueName);
        while (running) {
            try {
                String jobId = queue.poll(5, TimeUnit.SECONDS);
                if (jobId != null) {
                    log.info("Picked up retry job {} from queue", jobId);
                    webhookExecutionService.execute(UUID.fromString(jobId));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!running) break;
                log.error("Error processing retry queue item", e);
            }
        }
    }
}
