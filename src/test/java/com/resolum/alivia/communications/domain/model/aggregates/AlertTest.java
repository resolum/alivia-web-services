package com.resolum.alivia.communications.domain.model.aggregates;

import com.resolum.alivia.communications.domain.exceptions.InvalidAlertStatusTransition;
import com.resolum.alivia.communications.domain.exceptions.InvalidMessageContent;
import com.resolum.alivia.communications.domain.model.valueobjects.AlertStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AlertTest {

    @Test
    void createsAlertWithPendingStatusAndZeroAttempts() {
        Alert alert = new Alert(new UserId(10L), "req-123", "Emergencia", "Caída detectada");

        assertEquals(new UserId(10L), alert.getRecipientId());
        assertEquals("req-123", alert.getHelpRequestId());
        assertEquals("Emergencia", alert.getTitle());
        assertEquals("Caída detectada", alert.getBody());
        assertEquals(AlertStatus.PENDING, alert.getStatus());
        assertEquals(0, alert.getAttempts());
        assertNull(alert.getSentAt());
    }

    @Test
    void rejectsEmptyTitleOrBody() {
        assertThrows(InvalidMessageContent.class, () ->
                new Alert(new UserId(10L), "req-123", "", "Body"));
        assertThrows(InvalidMessageContent.class, () ->
                new Alert(new UserId(10L), "req-123", "Title", "   "));
    }

    @Test
    void markSentTransitionsToSent() {
        Alert alert = new Alert(new UserId(10L), "req-123", "Emergencia", "Alerta");
        LocalDateTime now = LocalDateTime.now();
        alert.markSent(now);

        assertEquals(AlertStatus.SENT, alert.getStatus());
        assertEquals(now, alert.getSentAt());
    }

    @Test
    void markUndeliveredIncrementsAttempts() {
        Alert alert = new Alert(new UserId(10L), "req-123", "Emergencia", "Alerta");
        alert.markUndelivered();

        assertEquals(AlertStatus.UNDELIVERED, alert.getStatus());
        assertEquals(1, alert.getAttempts());
        assertTrue(alert.canRetry(5));

        alert.markUndelivered();
        assertEquals(2, alert.getAttempts());
    }

    @Test
    void cannotTransitionFromSent() {
        Alert alert = new Alert(new UserId(10L), "req-123", "Emergencia", "Alerta");
        alert.markSent(LocalDateTime.now());

        assertThrows(InvalidAlertStatusTransition.class, alert::markUndelivered);
    }
}
