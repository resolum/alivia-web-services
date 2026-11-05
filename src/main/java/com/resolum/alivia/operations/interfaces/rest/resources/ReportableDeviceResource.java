package com.resolum.alivia.operations.interfaces.rest.resources;


/** An installed device the family can report, numbered from 1 in the order it was installed. */
public record ReportableDeviceResource(Long id, String alias, String macAddress, String type) {
}
