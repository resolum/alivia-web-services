package com.resolum.alivia.shared;

import com.resolum.alivia.shared.infrastructure.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final AtomicReference<String> idDuringRequest = new AtomicReference<>();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesTheIncomingCorrelationId() throws Exception {
        var response = new MockHttpServletResponse();

        filter.doFilter(requestWithHeader("req-123"), response, (request, result) ->
                idDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertEquals("req-123", idDuringRequest.get());
        assertEquals("req-123", response.getHeader(CorrelationIdFilter.HEADER));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void generatesAnIdWhenTheHeaderIsMissing() throws Exception {
        var response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/plans"), response, (request, result) ->
                idDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertNotNull(idDuringRequest.get());
        assertEquals(idDuringRequest.get(), response.getHeader(CorrelationIdFilter.HEADER));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void ignoresUnsafeHeaderValues() throws Exception {
        var response = new MockHttpServletResponse();

        filter.doFilter(requestWithHeader("bad\nvalue"), response, (request, result) ->
                idDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertNotEquals("bad\nvalue", idDuringRequest.get());
        assertFalse(idDuringRequest.get().contains("\n"));
    }

    @Test
    void clearsTheMdcWhenTheRequestFails() {
        var request = new MockHttpServletRequest("GET", "/api/v1/plans");

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request,
                new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
                    throw new IllegalStateException("boom");
                }));

        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private static MockHttpServletRequest requestWithHeader(String correlationId) {
        var request = new MockHttpServletRequest("GET", "/api/v1/plans");
        request.addHeader(CorrelationIdFilter.HEADER, correlationId);
        return request;
    }
}
