package com.resolum.alivia.hcm.interfaces.rest.transform;

import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.interfaces.rest.resources.ContractResource;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class ContractResourceAssembler {
    private final Clock clock;

    public ContractResourceAssembler(Clock clock) {
        this.clock = clock;
    }

    public ContractResource toResource(Contract contract) {
        var today = LocalDate.now(clock);
        return new ContractResource(contract.getId(), contract.getCode(), contract.getEmployeeId(),
                contract.getBusinessId(), contract.getPosition(), contract.getModality(),
                contract.getWeeklyHours(), contract.getStartDate(), contract.getEndDate(),
                contract.viewStatus(today), contract.isEligibleOn(today), contract.getNotes(),
                contract.getCreatedAt(), contract.getUpdatedAt());
    }
}
