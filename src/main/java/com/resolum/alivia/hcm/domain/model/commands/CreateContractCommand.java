package com.resolum.alivia.hcm.domain.model.commands;

import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import java.time.LocalDate;

public record CreateContractCommand(Long employeeId, Long businessId, String code, String position,
        ContractModality modality, Integer weeklyHours, LocalDate startDate, LocalDate endDate,
        ContractStatus initialStatus, String notes, Long actorUserId) {

    public CreateContractCommand(Long employeeId, Long businessId, String position,
            ContractModality modality, Integer weeklyHours, LocalDate startDate, LocalDate endDate,
            ContractStatus initialStatus, String notes, Long actorUserId) {
        this(employeeId, businessId, null, position, modality, weeklyHours, startDate, endDate,
                initialStatus, notes, actorUserId);
    }
}
