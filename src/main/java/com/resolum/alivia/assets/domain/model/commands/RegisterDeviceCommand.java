package com.resolum.alivia.assets.domain.model.commands;

public record RegisterDeviceCommand(Long businessId, String macAddress, String alias, String deviceType) {

    public RegisterDeviceCommand {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be a positive number.");
        }
        if (macAddress == null || macAddress.isBlank()) {
            throw new IllegalArgumentException("Mac address cannot be null or blank.");
        }
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Device alias cannot be null or blank.");
        }
        if (deviceType == null || deviceType.isBlank()) {
            throw new IllegalArgumentException("Device type cannot be null or blank.");
        }
    }
}
