package com.resolum.alivia.hcm.domain.model.commands;

public record TerminateContractCommand(Long employeeId, Long contractId, Long businessId,
                                       Long actorUserId) {
}
