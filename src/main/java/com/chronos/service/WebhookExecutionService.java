package com.chronos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookExecutionService {

    private final WebhookExecutionStore store;
    private final RestTemplate restTemplate;

    /**
     * Executes the webhook for the given job.
     * TX-1 (beginExecution) and TX-2 (finalize*) are separate commits.
     * The HTTP call runs between them — no DB connection held during I/O.
     */
    public void execute(UUID jobId) {
        WebhookExecutionStore.ExecutionPrep prep = store.beginExecution(jobId);
        if (prep == null) {
            log.debug("Skipping execution for job {} — not found or not PENDING", jobId);
            return;
        }

        log.info("Executing webhook for job {} (attempt via {})", jobId, prep.webhookUrl());

        String responseBody = null;
        String error = null;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(prep.webhookUrl(), null, String.class);
            responseBody = response.getBody();
        } catch (Exception e) {
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Webhook call failed for job {}: {}", jobId, error);
        }

        if (error == null) {
            log.info("Job {} completed successfully", jobId);
            store.finalizeSuccess(jobId, prep.execLogId(), responseBody);
        } else {
            store.finalizeFailure(jobId, prep.execLogId(), error);
        }
    }
}
