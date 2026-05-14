package com.chronos.service;

import com.chronos.domain.DlqEntry;
import com.chronos.domain.Job;
import com.chronos.domain.JobStatus;
import com.chronos.notification.NotificationService;
import com.chronos.repository.DlqRepository;
import com.chronos.repository.JobRepository;
import com.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetryService {

    private final JobRepository jobRepository;
    private final DlqRepository dlqRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RedissonClient redissonClient;

    @Value("${app.retry.queue:job:retry}")
    private String retryQueueName;

    @Transactional
    public void handleFailure(Job job, String errorMessage) {
        if (job.getRetryCount() < job.getMaxRetries()) {
            job.incrementRetryCount();
            job.updateStatus(JobStatus.PENDING);
            jobRepository.save(job);

            RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(retryQueueName);
            RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
            delayedQueue.offer(job.getId().toString(), job.getRetryDelayMs(), TimeUnit.MILLISECONDS);
            log.info("Job {} scheduled for retry #{} in {}ms", job.getId(), job.getRetryCount(), job.getRetryDelayMs());
        } else {
            job.updateStatus(JobStatus.DEAD);
            jobRepository.save(job);
            dlqRepository.save(new DlqEntry(job.getId(), errorMessage));
            log.warn("Job {} moved to DLQ after {} retries: {}", job.getId(), job.getRetryCount(), errorMessage);

            userRepository.findById(job.getUserId()).ifPresent(user ->
                    notificationService.sendJobDead(user.getEmail(), job.getName(), errorMessage));
        }
    }
}
