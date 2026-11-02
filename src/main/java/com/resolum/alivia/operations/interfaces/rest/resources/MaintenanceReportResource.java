package com.resolum.alivia.operations.interfaces.rest.resources;

public record MaintenanceReportResource(String diagnosis, String maintenancePerformed, String testResult,
                                        String resolution) {
}
