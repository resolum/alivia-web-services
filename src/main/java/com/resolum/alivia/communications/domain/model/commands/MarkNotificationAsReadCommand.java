package com.resolum.alivia.communications.domain.model.commands;

public record MarkNotificationAsReadCommand(Long notificationId, Long userId) {
}
