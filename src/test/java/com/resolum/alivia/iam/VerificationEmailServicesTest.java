package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.outboundservices.notifications.LoggingVerificationEmailService;
import com.resolum.alivia.iam.application.internal.outboundservices.notifications.UnavailableVerificationEmailService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerificationEmailServicesTest {

    @Test
    void loggingVerificationEmailServiceLogsWithoutException() {
        var service = new LoggingVerificationEmailService();
        assertDoesNotThrow(() -> service.sendVerificationEmail("user@example.com", "token123", "https://alivia.app/verify"));
    }

    @Test
    void unavailableVerificationEmailServiceThrowsException() {
        var service = new UnavailableVerificationEmailService();
        assertThrows(IllegalStateException.class, () ->
                service.sendVerificationEmail("user@example.com", "token123", "https://alivia.app/verify"));
    }
}
