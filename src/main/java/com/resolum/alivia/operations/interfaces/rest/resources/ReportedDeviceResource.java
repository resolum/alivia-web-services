package com.resolum.alivia.operations.interfaces.rest.resources;


/** The installed device an incident is about, as it was when reported. */
public record ReportedDeviceResource(String alias, String macAddress, String type) {
}
