package com.resolum.alivia.hcm.domain.services;

import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.entities.ContractVersion;
import com.resolum.alivia.hcm.domain.model.queries.GetContractQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetContractsQuery;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface ContractQueryService {
    Page<Contract> handle(GetContractsQuery query);
    Contract handle(GetContractQuery query);
    List<ContractVersion> history(GetContractQuery query);
    boolean isEmployeeEligible(Long employeeId, Long businessId, LocalDate date);
}
