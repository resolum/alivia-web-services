package com.resolum.alivia.hcm.interfaces.rest.resources;

import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractViewStatus;

import java.time.LocalDate;

public record AssociatedContractResource(Long id, String code, ContractModality modality, Integer weeklyHours,
                                         LocalDate startDate, LocalDate endDate, ContractViewStatus status) {
}
