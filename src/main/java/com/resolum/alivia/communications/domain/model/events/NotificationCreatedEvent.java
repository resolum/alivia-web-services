package com.resolum.alivia.communications.domain.model.events;

import com.resolum.alivia.communications.domain.model.aggregates.Notification;

public class NotificationCreatedEvent {
    private final Notification notification;

    public NotificationCreatedEvent(Notification notification) {
        this.notification = notification;
    }

    public Notification getNotification() {
        return notification;
    }

    public Long getNotificationId() {
        return notification != null ? notification.getId() : null;
    }
}
