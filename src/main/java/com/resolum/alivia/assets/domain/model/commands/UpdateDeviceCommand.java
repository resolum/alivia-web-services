package com.resolum.alivia.assets.domain.model.commands;

public record UpdateDeviceCommand(Long deviceId,String macAddress,String deviceType) {
}
