package com.resolum.alivia.operations.interfaces.rest.resources;

import com.resolum.alivia.operations.domain.model.valueobjects.TestResult;
import jakarta.validation.constraints.Size;

/** The test result is checked by the domain, which answers 400 and keeps the order open when it is missing. */
public record CompleteMaintenanceOrderResource(@Size(max = 500) String diagnosis,
                                               @Size(max = 500) String maintenancePerformed,
                                               TestResult testResult,
                                               @Size(max = 500) String resolution) {
}
