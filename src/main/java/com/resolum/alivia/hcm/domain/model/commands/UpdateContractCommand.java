package com.resolum.alivia.hcm.domain.model.commands;

import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import java.time.LocalDate;

public record UpdateContractCommand(Long employeeId, Long contractId, Long businessId,
        String position, ContractModality modality, Integer weeklyHours,
        LocalDate startDate, LocalDate endDate, String notes, Long actorUserId) {
}
