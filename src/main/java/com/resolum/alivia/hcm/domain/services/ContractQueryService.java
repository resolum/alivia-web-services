package com.resolum.alivia.hcm.domain.services;

import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.queries.GetContractQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetContractsQuery;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface ContractQueryService {
    Page<Contract> handle(GetContractsQuery query);
    Contract handle(GetContractQuery query);
    boolean isEmployeeEligible(Long employeeId, Long businessId, LocalDate date);
}
