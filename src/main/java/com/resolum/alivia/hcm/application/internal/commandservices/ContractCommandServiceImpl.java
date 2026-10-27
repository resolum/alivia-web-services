package com.resolum.alivia.hcm.application.internal.commandservices;

import com.resolum.alivia.hcm.domain.exceptions.ContractNotFound;
import com.resolum.alivia.hcm.domain.exceptions.ContractOverlap;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.commands.CreateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.RenewContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.TerminateContractCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateContractCommand;
import com.resolum.alivia.hcm.domain.model.entities.ContractVersion;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractAction;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractPeriod;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractStatus;
import com.resolum.alivia.hcm.domain.services.ContractCommandService;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractVersionRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ContractCommandServiceImpl implements ContractCommandService {
    private static final EnumSet<ContractStatus> INCOMPATIBLE_STATUSES =
            EnumSet.of(ContractStatus.PENDING, ContractStatus.ACTIVE);

    private final ContractPersistenceRepository contracts;
    private final ContractVersionRepository versions;
    private final EmployeeRepository employees;
    private final Clock clock;

    public ContractCommandServiceImpl(ContractPersistenceRepository contracts,
                                      ContractVersionRepository versions,
                                      EmployeeRepository employees, Clock clock) {
        this.contracts = contracts;
        this.versions = versions;
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
        recordVersion(contract, ContractAction.CREATED, command.actorUserId());
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
        recordVersion(contract, ContractAction.UPDATED, command.actorUserId());
        return contract;
    }

    @Override
    @Transactional
    public Contract handle(RenewContractCommand command) {
        validateEmployee(command.employeeId(), command.businessId());
        var contract = getOwned(command.employeeId(), command.contractId(), command.businessId());
        var period = new ContractPeriod(command.startDate(), command.endDate());
        ensureNoOverlap(command.employeeId(), period, contract.getId());
        contract.renew(period);
        contract = contracts.save(contract);
        recordVersion(contract, ContractAction.RENEWED, command.actorUserId());
        return contract;
    }

    @Override
    @Transactional
    public Contract handle(TerminateContractCommand command) {
        validateEmployee(command.employeeId(), command.businessId());
        var contract = getOwned(command.employeeId(), command.contractId(), command.businessId());
        contract.terminate();
        contract = contracts.save(contract);
        recordVersion(contract, ContractAction.TERMINATED, command.actorUserId());
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

    private void recordVersion(Contract contract, ContractAction action, Long actorUserId) {
        int nextVersion = Math.toIntExact(versions.countByContractId(contract.getId()) + 1);
        versions.save(new ContractVersion(contract.getId(), nextVersion, action, actorUserId,
                snapshot(contract)));
    }

    private static Map<String, String> snapshot(Contract contract) {
        var snapshot = new LinkedHashMap<String, String>();
        snapshot.put("code", contract.getCode());
        snapshot.put("employeeId", String.valueOf(contract.getEmployeeId()));
        snapshot.put("position", contract.getPosition());
        snapshot.put("modality", contract.getModality().name());
        snapshot.put("weeklyHours", String.valueOf(contract.getWeeklyHours()));
        snapshot.put("startDate", contract.getStartDate().toString());
        snapshot.put("endDate", contract.getEndDate().toString());
        snapshot.put("status", contract.getStatus().name());
        snapshot.put("notes", String.valueOf(contract.getNotes()));
        return snapshot;
    }
}
