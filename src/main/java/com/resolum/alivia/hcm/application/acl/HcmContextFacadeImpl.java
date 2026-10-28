package com.resolum.alivia.hcm.application.acl;

import com.resolum.alivia.hcm.application.internal.outboundservices.acl.ExternalProfilesService;
import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkShift;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.ContractPersistenceRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.EmployeeRepository;
import com.resolum.alivia.hcm.infrastructure.persistence.jpa.repositories.WorkSchedulePersistenceRepository;
import com.resolum.alivia.hcm.interfaces.acl.HcmContextFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Reads technicians, contracts and work schedules of the default business for other contexts. */
@Service
public class HcmContextFacadeImpl implements HcmContextFacade {
    private final EmployeeRepository employees;
    private final ContractPersistenceRepository contracts;
    private final WorkSchedulePersistenceRepository schedules;
    private final ExternalProfilesService profiles;
    private final Long businessId;

    public HcmContextFacadeImpl(EmployeeRepository employees, ContractPersistenceRepository contracts,
                                WorkSchedulePersistenceRepository schedules, ExternalProfilesService profiles,
                                @Value("${alivia.hcm.default-business-id}") Long businessId) {
        this.employees = employees;
        this.contracts = contracts;
        this.schedules = schedules;
        this.profiles = profiles;
        this.businessId = businessId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianShifts> fetchTechniciansWorkingOn(LocalDate date) {
        var activeSchedules = schedules.findByBusinessIdAndStatus(businessId, WorkScheduleStatus.ACTIVE).stream()
                .filter(schedule -> schedule.isInEffectOn(date))
                .toList();
        return employees.findAll().stream()
                .filter(employee -> employee.belongsTo(businessId)
                        && employee.getRole() == EmployeeRole.TECH
                        && employee.getStatus() == EmployeeStatus.ACTIVE
                        && contracts.findByEmployeeId(employee.getId()).stream().anyMatch(contract -> contract.isEligibleOn(date)))
                .flatMap(employee -> scheduleOf(employee, activeSchedules)
                        .map(schedule -> toTechnician(employee, schedule, date)).stream())
                .filter(technician -> !technician.shifts().isEmpty())
                .sorted(Comparator.comparing(TechnicianShifts::employeeId))
                .toList();
    }

    private static Optional<WorkSchedule> scheduleOf(Employee employee, List<WorkSchedule> activeSchedules) {
        return activeSchedules.stream().filter(schedule -> schedule.getEmployeeId().equals(employee.getId())).findFirst();
    }

    private TechnicianShifts toTechnician(Employee employee, WorkSchedule schedule, LocalDate date) {
        var shifts = schedule.getShifts().stream()
                .filter(shift -> shift.day().equals(date.getDayOfWeek()))
                .sorted(Comparator.comparing(WorkShift::startTime))
                .map(WorkShift::range)
                .toList();
        var name = profiles.fetchStaffProfile(employee.getStaffProfileId())
                .map(profile -> profile.firstName() + " " + profile.lastName())
                .orElse("Técnico Alivia");
        return new TechnicianShifts(employee.getId(), name, shifts);
    }
}
