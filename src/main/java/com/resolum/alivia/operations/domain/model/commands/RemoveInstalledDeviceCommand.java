package com.resolum.alivia.operations.domain.model.commands;

public record RemoveInstalledDeviceCommand(Long orderId, String macAddress) {
}
