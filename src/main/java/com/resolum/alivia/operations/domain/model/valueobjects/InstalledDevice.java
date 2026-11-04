package com.resolum.alivia.operations.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Device the technician registers during an installation visit: a descriptive alias for where it sits, its
 * MAC address in the {@code 00:1A:2B:3C:4D:5E} form and its kind.
 */
@Embeddable
public record InstalledDevice(
        @Column(name = "alias", nullable = false, length = 60) String alias,
        @Column(name = "mac_address", nullable = false, length = 17) String macAddress,
        @Enumerated(EnumType.STRING) @Column(name = "device_type", nullable = false, length = 16) InstalledDeviceType type) {
    private static final Pattern MAC = Pattern.compile("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");

    public InstalledDevice {
        alias = alias == null ? null : alias.trim();
        if (alias == null || alias.isEmpty()) throw new IllegalArgumentException("The device alias is required.");
        if (alias.length() > 60) throw new IllegalArgumentException("The device alias cannot exceed 60 characters.");
        macAddress = normalizeMac(macAddress);
        if (!MAC.matcher(macAddress).matches()) {
            throw new IllegalArgumentException("The MAC address needs 12 hexadecimal digits in pairs separated by colons.");
        }
        if (type == null) throw new IllegalArgumentException("The device type is required.");
    }

    /** Upper-cases the address and accepts dashes as separators, so {@code 00-1a-2b-3c-4d-5e} is the same device. */
    public static String normalizeMac(String macAddress) {
        return macAddress == null ? "" : macAddress.trim().replace('-', ':').toUpperCase(Locale.ROOT);
    }
}
