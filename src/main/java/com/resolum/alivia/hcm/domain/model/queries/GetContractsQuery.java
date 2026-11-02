package com.resolum.alivia.hcm.domain.model.queries;

import com.resolum.alivia.hcm.domain.model.valueobjects.ContractModality;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractViewStatus;
import org.springframework.data.domain.Pageable;

public record GetContractsQuery(Long businessId, Long employeeId, ContractViewStatus status,
                                ContractModality modality, String search, Pageable pageable) {
}
