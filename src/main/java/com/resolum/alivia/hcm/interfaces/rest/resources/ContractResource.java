package com.resolum.alivia.hcm.interfaces.rest.resources;

import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractViewStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ContractResource(Long id, String code, Long employeeId, Long businessId,
        String position, ContractModality modality, Integer weeklyHours,
        LocalDate startDate, LocalDate endDate, ContractViewStatus status,
        boolean eligible, String notes, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
