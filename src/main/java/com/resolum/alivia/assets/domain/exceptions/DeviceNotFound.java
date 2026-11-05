package com.resolum.alivia.assets.domain.exceptions;

public class DeviceNotFound extends RuntimeException {
    public DeviceNotFound(Long deviceId) {
        super(String.format("device with id %d not found",deviceId));
    }
}
