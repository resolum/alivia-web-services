package com.resolum.alivia.operations.domain.exceptions;

public class InstalledDeviceAlreadyRegistered extends RuntimeException {
    public InstalledDeviceAlreadyRegistered(String macAddress) {
        super("The device %s is already registered in this order.".formatted(macAddress));
    }
}
