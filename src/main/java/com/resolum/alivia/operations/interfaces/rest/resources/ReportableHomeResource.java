package com.resolum.alivia.operations.interfaces.rest.resources;

import java.util.List;

/**
 * The home of the account for the report form: its installed devices, the address of the visits and whether
 * photos can be attached (false while photo storage is not configured).
 */
public record ReportableHomeResource(String draftId, String directions, String contactName,
                                     List<ReportableDeviceResource> devices, boolean photoUploadEnabled) {
}
