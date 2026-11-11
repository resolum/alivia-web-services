package com.resolum.alivia.communications.domain.model.commands;

public record UnregisterPushDeviceCommand(Long userId, String token) {
}
