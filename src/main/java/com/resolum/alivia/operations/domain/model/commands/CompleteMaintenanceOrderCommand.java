package com.resolum.alivia.operations.domain.model.commands;

import com.resolum.alivia.operations.domain.model.valueobjects.TestResult;

public record CompleteMaintenanceOrderCommand(Long orderId, String diagnosis, String maintenancePerformed,
                                              TestResult testResult, String resolution) {
}
