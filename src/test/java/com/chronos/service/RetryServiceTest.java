package com.chronos.service;

import com.chronos.domain.Job;
import com.chronos.domain.JobStatus;
import com.chronos.domain.User;
import com.chronos.notification.NotificationService;
import com.chronos.repository.DlqRepository;
import com.chronos.repository.JobRepository;
import com.chronos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private DlqRepository dlqRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private RedissonClient redissonClient;
    @Mock private RBlockingQueue<String> blockingQueue;
    @Mock private RDelayedQueue<String> delayedQueue;

    @InjectMocks private RetryService retryService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(1);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(retryService, "retryQueueName", "job:retry");
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Job pendingJob(int maxRetries) {
        Job job = new Job(USER_ID, "My Job", null, "https://example.com/hook", null, FUTURE, maxRetries, 1000L);
        ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
        return job;
    }

    @Test
    void handleFailure_enqueues_retry_when_retries_remaining() {
        doReturn(blockingQueue).when(redissonClient).getBlockingQueue(anyString());
        doReturn(delayedQueue).when(redissonClient).getDelayedQueue(blockingQueue);
        Job job = pendingJob(3);

        retryService.handleFailure(job, "HTTP 500");

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getRetryCount()).isEqualTo(1);
        verify(delayedQueue).offer(eq(job.getId().toString()), eq(1000L), any());
        verifyNoInteractions(dlqRepository, notificationService);
    }

    @Test
    void handleFailure_moves_to_dlq_when_retries_exhausted() {
        Job job = pendingJob(0);
        User user = new User("alice", "alice@example.com", "hash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        retryService.handleFailure(job, "HTTP 500");

        assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD);
        verify(dlqRepository).save(any());
        verify(notificationService).sendJobDead("alice@example.com", "My Job", "HTTP 500");
        verifyNoInteractions(redissonClient);
    }

    @Test
    void handleFailure_moves_to_dlq_silently_when_user_not_found() {
        Job job = pendingJob(0);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        retryService.handleFailure(job, "error");

        assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD);
        verify(dlqRepository).save(any());
        verifyNoInteractions(notificationService, redissonClient);
    }
}
