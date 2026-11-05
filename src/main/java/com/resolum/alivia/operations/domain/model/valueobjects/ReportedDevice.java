package com.resolum.alivia.operations.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The installed device an incident is about, copied when it is reported so the ticket keeps naming it even if
 * the installation changes later.
 */
@Embeddable
public record ReportedDevice(
        @Column(name = "device_alias", length = 60) String alias,
        @Column(name = "device_mac", length = 17) String macAddress,
        @Column(name = "device_type", length = 16) String type) {

    public ReportedDevice {
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("The device alias is required.");
        if (macAddress == null || macAddress.isBlank()) throw new IllegalArgumentException("The device MAC address is required.");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("The device type is required.");
    }

    public static ReportedDevice of(InstalledDevice device) {
        return new ReportedDevice(device.alias(), device.macAddress(), device.type().name());
    }
}
