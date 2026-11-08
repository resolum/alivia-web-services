package com.resolum.alivia.communications.domain.exceptions;

public class NotificationNotFound extends RuntimeException {
    public NotificationNotFound(Long notificationId) {
        super("Notification not found with id: " + notificationId);
    }
}
