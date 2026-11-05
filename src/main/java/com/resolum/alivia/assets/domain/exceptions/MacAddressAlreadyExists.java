package com.resolum.alivia.assets.domain.exceptions;

public class MacAddressAlreadyExists extends RuntimeException {
    public MacAddressAlreadyExists(String macAddress) {

        super(String.format("mac address %s already exists", macAddress));
    }
}
