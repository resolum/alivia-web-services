package com.resolum.alivia.communications.domain.exceptions;

public class NotificationNotForUser extends RuntimeException {
    public NotificationNotForUser(Long notificationId) {
        super("Notification " + notificationId + " does not belong to the user.");
    }
}
