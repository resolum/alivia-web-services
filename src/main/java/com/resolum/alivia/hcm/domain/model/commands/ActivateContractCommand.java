package com.resolum.alivia.hcm.domain.model.commands;

public record ActivateContractCommand(Long employeeId, Long contractId, Long businessId,
                                      Long actorUserId) {
}
