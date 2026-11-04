package com.resolum.alivia.operations.interfaces.rest.resources;

/** Device registered during an installation visit. */
public record InstalledDeviceResource(String alias, String macAddress, String type) {
}
