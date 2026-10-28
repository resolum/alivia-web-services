package com.resolum.alivia.hcm.domain.model.commands;

import java.time.LocalDate;

public record RenewContractCommand(Long employeeId, Long contractId, Long businessId,
                                   LocalDate startDate, LocalDate endDate, Long actorUserId) {
}
