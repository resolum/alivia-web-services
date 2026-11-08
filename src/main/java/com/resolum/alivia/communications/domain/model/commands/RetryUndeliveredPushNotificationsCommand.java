package com.resolum.alivia.communications.domain.model.commands;

public record RetryUndeliveredPushNotificationsCommand(Integer maxAttempts) {
}
