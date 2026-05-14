package com.chronos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookExecutionServiceTest {

    @Mock private WebhookExecutionStore store;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private WebhookExecutionService service;

    private static final UUID JOB_ID      = UUID.randomUUID();
    private static final UUID EXEC_LOG_ID = UUID.randomUUID();
    private static final UUID USER_ID     = UUID.randomUUID();
    private static final String URL       = "https://example.com/hook";

    private WebhookExecutionStore.ExecutionPrep prep() {
        return new WebhookExecutionStore.ExecutionPrep(EXEC_LOG_ID, URL, USER_ID, "My Job");
    }

    @Test
    void execute_skips_when_store_returns_null() {
        when(store.beginExecution(JOB_ID)).thenReturn(null);

        service.execute(JOB_ID);

        verifyNoInteractions(restTemplate);
        verify(store, never()).finalizeSuccess(any(), any(), any());
        verify(store, never()).finalizeFailure(any(), any(), any());
    }

    @Test
    void execute_calls_finalize_success_and_passes_response_body() {
        when(store.beginExecution(JOB_ID)).thenReturn(prep());
        when(restTemplate.postForEntity(URL, null, String.class))
                .thenReturn(ResponseEntity.ok("hook response"));

        service.execute(JOB_ID);

        verify(store).finalizeSuccess(JOB_ID, EXEC_LOG_ID, "hook response");
        verify(store, never()).finalizeFailure(any(), any(), any());
    }

    @Test
    void execute_passes_null_body_when_webhook_returns_empty() {
        when(store.beginExecution(JOB_ID)).thenReturn(prep());
        when(restTemplate.postForEntity(URL, null, String.class))
                .thenReturn(ResponseEntity.ok(null));

        service.execute(JOB_ID);

        verify(store).finalizeSuccess(JOB_ID, EXEC_LOG_ID, null);
    }

    @Test
    void execute_calls_finalize_failure_on_exception() {
        when(store.beginExecution(JOB_ID)).thenReturn(prep());
        when(restTemplate.postForEntity(URL, null, String.class))
                .thenThrow(new RuntimeException("Connection refused"));

        service.execute(JOB_ID);

        verify(store).finalizeFailure(eq(JOB_ID), eq(EXEC_LOG_ID), contains("Connection refused"));
        verify(store, never()).finalizeSuccess(any(), any(), any());
    }

    @Test
    void execute_uses_class_name_as_error_when_exception_has_no_message() {
        when(store.beginExecution(JOB_ID)).thenReturn(prep());
        when(restTemplate.postForEntity(URL, null, String.class))
                .thenThrow(new NullPointerException());

        service.execute(JOB_ID);

        verify(store).finalizeFailure(eq(JOB_ID), eq(EXEC_LOG_ID), eq("NullPointerException"));
    }
}
