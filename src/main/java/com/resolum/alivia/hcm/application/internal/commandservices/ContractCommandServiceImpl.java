package com.resolum.alivia.hcm.application.internal.commandservices;

import com.resolum.alivia.hcm.domain.exceptions.ContractNotFound;
import com.resolum.alivia.hcm.domain.exceptions.ContractOverlap;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.commands.CreateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateContractCommand;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.services.ContractCommandService;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;

@Service
public class ContractCommandServiceImpl implements ContractCommandService {
    private static final EnumSet<ContractStatus> INCOMPATIBLE_STATUSES =
            EnumSet.of(ContractStatus.PENDING, ContractStatus.ACTIVE);

    private final ContractPersistenceRepository contracts;
    private final EmployeeRepository employees;
    private final Clock clock;

    public ContractCommandServiceImpl(ContractPersistenceRepository contracts,
                                      EmployeeRepository employees, Clock clock) {
        this.contracts = contracts;
        this.employees = employees;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Contract handle(CreateContractCommand command) {
        validateEmployee(command.employeeId(), command.businessId());
        var period = new ContractPeriod(command.startDate(), command.endDate());
        ensureNoOverlap(command.employeeId(), period, null);
        var contract = new Contract(command.employeeId(), command.businessId(), command.position(),
                command.modality(), command.weeklyHours(), period,
                command.initialStatus() == null ? ContractStatus.PENDING : command.initialStatus(),
                command.notes());
        var requestedCode = command.code() == null ? "" : command.code().trim().toUpperCase();
        if (!requestedCode.isBlank()) {
            if (contracts.existsByCodeIgnoreCase(requestedCode)) {
                throw new IllegalArgumentException("Contract code is already registered.");
            }
            contract.assignCode(requestedCode);
            contract = contracts.saveAndFlush(contract);
        } else {
            contract = contracts.saveAndFlush(contract);
            contract.assignCode("CTR-ALV-%d-%04d".formatted(
                    LocalDate.now(clock).getYear(), contract.getId()));
        }
        contract = contracts.save(contract);
        return contract;
    }

    @Override
    @Transactional
    public Contract handle(UpdateContractCommand command) {
        validateEmployee(command.employeeId(), command.businessId());
        var contract = getOwned(command.employeeId(), command.contractId(), command.businessId());
        var period = new ContractPeriod(command.startDate(), command.endDate());
        ensureNoOverlap(command.employeeId(), period, contract.getId());
        contract.update(command.position(), command.modality(), command.weeklyHours(), period, command.notes());
        contract = contracts.save(contract);
        return contract;
    }

    private Employee validateEmployee(Long employeeId, Long businessId) {
        var employee = employees.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFound(employeeId));
        if (!employee.belongsTo(businessId)) throw new EmployeeNotInBusiness(employeeId, businessId);
        return employee;
    }

    private Contract getOwned(Long employeeId, Long contractId, Long businessId) {
        var contract = contracts.findById(contractId).orElseThrow(() -> new ContractNotFound(contractId));
        if (!contract.getEmployeeId().equals(employeeId) || !contract.getBusinessId().equals(businessId)) {
            throw new ContractNotFound(contractId);
        }
        return contract;
    }

    private void ensureNoOverlap(Long employeeId, ContractPeriod period, Long excludedId) {
        if (contracts.existsOverlapping(employeeId, period.startDate(), period.endDate(),
                INCOMPATIBLE_STATUSES, excludedId)) {
            throw new ContractOverlap(employeeId);
        }
    }

}
