package com.resolum.alivia.operations.domain.model.commands;

import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDeviceType;

/**
 * Registers a device the technician installed at the home.
 *
 * @param room key of the room where a device (not an edge) is placed, such as {@code habitacion-1}; null for edges
 */
public record RegisterInstalledDeviceCommand(Long orderId, String alias, String macAddress, InstalledDeviceType type,
                                             String room) {

    public RegisterInstalledDeviceCommand(Long orderId, String alias, String macAddress, InstalledDeviceType type) {
        this(orderId, alias, macAddress, type, null);
    }
}
