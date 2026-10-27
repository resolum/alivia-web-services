package com.resolum.alivia.hcm.application.internal.commandservices;

import com.resolum.alivia.hcm.domain.exceptions.ContractNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleNotFound;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleOutsideContract;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleOverlap;
import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.commands.CreateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.commands.DeactivateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateWorkScheduleCommand;
import com.resolum.alivia.hcm.domain.model.valueobjects.ContractViewStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkSchedulePeriod;
import com.resolum.alivia.hcm.domain.services.WorkScheduleCommandService;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.WorkSchedulePersistenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;

/**
 * A work schedule is configured inside a contract: the contract fixes the employee, the agreed weekly
 * hours and the period the schedule can cover.
 */
@Service
public class WorkScheduleCommandServiceImpl implements WorkScheduleCommandService {
    private static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);
    private static final EnumSet<ContractViewStatus> OPEN_CONTRACT_STATUSES =
            EnumSet.of(ContractViewStatus.PENDING, ContractViewStatus.ACTIVE);

    private final WorkSchedulePersistenceRepository workSchedules;
    private final ContractPersistenceRepository contracts;
    private final EmployeeRepository employees;
    private final Clock clock;

    public WorkScheduleCommandServiceImpl(WorkSchedulePersistenceRepository workSchedules,
                                          ContractPersistenceRepository contracts,
                                          EmployeeRepository employees, Clock clock) {
        this.workSchedules = workSchedules;
        this.contracts = contracts;
        this.employees = employees;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WorkSchedule handle(CreateWorkScheduleCommand command) {
        validateEmployee(command.employeeId(), command.businessId());
        var contract = getOwnedContract(command.employeeId(), command.contractId(), command.businessId());
        var period = new WorkSchedulePeriod(command.startDate(), command.endDate());
        ensureWithinContract(contract, period);
        var workSchedule = new WorkSchedule(command.employeeId(), command.businessId(), contract.getId(),
                command.name(), period, command.shifts(), command.actorUserId());
        if (!command.draft()) publish(workSchedule, null);
        workSchedule = workSchedules.saveAndFlush(workSchedule);
        workSchedule.assignCode("HOR-%d-%04d".formatted(LocalDate.now(clock).getYear(), workSchedule.getId()));
        return workSchedules.save(workSchedule);
    }

    @Override
    @Transactional
    public WorkSchedule handle(UpdateWorkScheduleCommand command) {
        validateEmployee(command.employeeId(), command.businessId());
        var workSchedule = getOwned(command.employeeId(), command.workScheduleId(), command.businessId());
        var contract = getOwnedContract(command.employeeId(), workSchedule.getContractId(), command.businessId());
        var period = new WorkSchedulePeriod(command.startDate(), command.endDate());
        ensureWithinContract(contract, period);
        workSchedule.update(command.name(), period, command.shifts(), command.actorUserId());
        if (!workSchedule.isDraft() || !command.draft()) publish(workSchedule, workSchedule.getId());
        return workSchedules.save(workSchedule);
    }

    @Override
    @Transactional
    public WorkSchedule handle(DeactivateWorkScheduleCommand command) {
        validateEmployee(command.employeeId(), command.businessId());
        var workSchedule = getOwned(command.employeeId(), command.workScheduleId(), command.businessId());
        workSchedule.deactivate(command.actorUserId());
        return workSchedules.save(workSchedule);
    }

    /** Puts the schedule into effect unless another active schedule of the employee covers the same days. */
    private void publish(WorkSchedule workSchedule, Long excludedId) {
        var period = workSchedule.getPeriod();
        if (workSchedules.existsActiveOverlapping(workSchedule.getEmployeeId(), period.startDate(),
                period.isOpenEnded() ? OPEN_END : period.endDate(), excludedId)) {
            throw new WorkScheduleOverlap(workSchedule.getEmployeeId());
        }
        workSchedule.activate();
    }

    private void ensureWithinContract(Contract contract, WorkSchedulePeriod period) {
        if (!OPEN_CONTRACT_STATUSES.contains(contract.viewStatus(LocalDate.now(clock)))
                || !period.fitsWithin(contract.getPeriod())) {
            throw new WorkScheduleOutsideContract(contract.getId());
        }
    }

    private void validateEmployee(Long employeeId, Long businessId) {
        var employee = employees.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFound(employeeId));
        if (!employee.belongsTo(businessId)) throw new EmployeeNotInBusiness(employeeId, businessId);
    }

    private Contract getOwnedContract(Long employeeId, Long contractId, Long businessId) {
        var contract = contracts.findById(contractId).orElseThrow(() -> new ContractNotFound(contractId));
        if (!contract.getEmployeeId().equals(employeeId) || !contract.getBusinessId().equals(businessId)) {
            throw new ContractNotFound(contractId);
        }
        return contract;
    }

    private WorkSchedule getOwned(Long employeeId, Long workScheduleId, Long businessId) {
        var workSchedule = workSchedules.findById(workScheduleId)
                .orElseThrow(() -> new WorkScheduleNotFound(workScheduleId));
        if (!workSchedule.getEmployeeId().equals(employeeId) || !workSchedule.getBusinessId().equals(businessId)) {
            throw new WorkScheduleNotFound(workScheduleId);
        }
        return workSchedule;
    }
}
