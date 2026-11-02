package com.resolum.alivia.operations.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * What the technician found and did in a maintenance visit. A visit cannot close without the result of its
 * test, so a report without it is rejected and the order stays open.
 */
@Embeddable
public record MaintenanceReport(
        @Column(name = "diagnosis", length = 500) String diagnosis,
        @Column(name = "maintenance_performed", length = 500) String maintenancePerformed,
        @Enumerated(EnumType.STRING) @Column(name = "test_result", length = 16) TestResult testResult,
        @Column(name = "resolution", length = 500) String resolution) {

    public MaintenanceReport {
        diagnosis = required(diagnosis, "The diagnosis is required.");
        maintenancePerformed = required(maintenancePerformed, "Describe the maintenance performed.");
        if (testResult == null) throw new IllegalArgumentException("The order cannot close without the test result.");
        resolution = resolution == null || resolution.isBlank() ? null : resolution.trim();
        if (resolution != null && resolution.length() > 500) {
            throw new IllegalArgumentException("The resolution cannot exceed 500 characters.");
        }
        if (testResult == TestResult.PASSED && resolution == null) {
            throw new IllegalArgumentException("Describe how the incident was resolved.");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank() || value.trim().length() > 500) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
