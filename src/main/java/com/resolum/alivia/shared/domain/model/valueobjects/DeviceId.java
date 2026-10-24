package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record DeviceId(Long deviceId) {

    public DeviceId {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("Device ID must be a positive number.");
        }
    }
}
