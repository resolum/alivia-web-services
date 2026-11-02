package com.resolum.alivia.operations.domain.model.commands;

import com.resolum.alivia.operations.domain.model.valueobjects.InstallationResult;

public record RecordInstallationResultCommand(Long orderId, InstallationResult result, String notes) {
}
