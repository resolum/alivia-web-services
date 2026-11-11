package com.resolum.alivia.communications.domain.model.commands;

public record RetryUndeliveredAlertsCommand(Integer maxAttempts) {
}
