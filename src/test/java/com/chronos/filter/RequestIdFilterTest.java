package com.chronos.filter;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void echoes_provided_request_id_and_populates_mdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "trace-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> capturedMdc.set(MDC.get("requestId")));

        assertThat(capturedMdc.get()).isEqualTo("trace-abc-123");
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("trace-abc-123");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void generates_uuid_when_header_absent_and_cleans_mdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> capturedMdc.set(MDC.get("requestId")));

        assertThat(capturedMdc.get()).isNotNull().isNotBlank();
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(capturedMdc.get());
        assertThat(MDC.get("requestId")).isNull();
    }
}
