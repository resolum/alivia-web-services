package com.resolum.alivia.communications.domain.model.commands;

import com.resolum.alivia.communications.domain.model.valueobjects.NotificationType;

public record CreateNotificationCommand(Long recipientUserId, NotificationType type, String sourceId, String title, String body) {
}
