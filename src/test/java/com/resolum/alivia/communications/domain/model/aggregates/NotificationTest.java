package com.resolum.alivia.communications.domain.model.aggregates;

import com.resolum.alivia.communications.domain.exceptions.InvalidMessageContent;
import com.resolum.alivia.communications.domain.exceptions.NotificationNotForUser;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationStatus;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationType;
import com.resolum.alivia.communications.domain.model.valueobjects.PushStatus;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {

    @Test
    void createsNotificationWithUnreadStatusAndPendingPush() {
        Notification notification = new Notification(
                new UserId(5L),
                NotificationType.LOW_BATTERY,
                "dev-01",
                "Batería baja",
                "Sensor con 10% de batería"
        );

        assertEquals(new UserId(5L), notification.getRecipientId());
        assertEquals(NotificationType.LOW_BATTERY, notification.getType());
        assertEquals(NotificationStatus.UNREAD, notification.getStatus());
        assertEquals(PushStatus.PENDING, notification.getPushStatus());
        assertEquals(0, notification.getPushAttempts());
        assertNull(notification.getReadAt());
    }

    @Test
    void rejectsEmptyContent() {
        assertThrows(InvalidMessageContent.class, () ->
                new Notification(new UserId(5L), NotificationType.LOW_BATTERY, "dev-01", " ", "Body"));
    }

    @Test
    void markAsReadSucceedsForRecipient() {
        Notification notification = new Notification(
                new UserId(5L),
                NotificationType.LOW_BATTERY,
                "dev-01",
                "Batería baja",
                "Sensor con 10% de batería"
        );
        LocalDateTime now = LocalDateTime.now();
        notification.markAsRead(new UserId(5L), now);

        assertEquals(NotificationStatus.READ, notification.getStatus());
        assertEquals(now, notification.getReadAt());
    }

    @Test
    void markAsReadThrowsForDifferentUser() {
        Notification notification = new Notification(
                new UserId(5L),
                NotificationType.LOW_BATTERY,
                "dev-01",
                "Batería baja",
                "Sensor con 10% de batería"
        );

        assertThrows(NotificationNotForUser.class, () ->
                notification.markAsRead(new UserId(99L), LocalDateTime.now()));
    }

    @Test
    void pushTransitionsAndRetries() {
        Notification notification = new Notification(
                new UserId(5L),
                NotificationType.LOW_BATTERY,
                "dev-01",
                "Batería baja",
                "Sensor con 10% de batería"
        );

        notification.markPushUndelivered();
        assertEquals(PushStatus.UNDELIVERED, notification.getPushStatus());
        assertEquals(1, notification.getPushAttempts());
        assertTrue(notification.canRetryPush(3));

        notification.markPushSent();
        assertEquals(PushStatus.SENT, notification.getPushStatus());
        assertFalse(notification.canRetryPush(3));
    }
}
