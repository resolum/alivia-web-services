package com.resolum.alivia.operations.domain.model.commands;

import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDeviceType;

public record RegisterInstalledDeviceCommand(Long orderId, String alias, String macAddress, InstalledDeviceType type) {
}
