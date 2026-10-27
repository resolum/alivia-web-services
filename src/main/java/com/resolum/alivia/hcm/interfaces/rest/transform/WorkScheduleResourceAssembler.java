package com.resolum.alivia.hcm.interfaces.rest.transform;

import com.resolum.alivia.hcm.domain.model.aggregates.Contract;
import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkShift;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkloadCompliance;
import com.resolum.alivia.hcm.interfaces.rest.resources.AssociatedContractResource;
import com.resolum.alivia.hcm.interfaces.rest.resources.WorkScheduleResource;
import com.resolum.alivia.hcm.interfaces.rest.resources.WorkShiftResource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class WorkScheduleResourceAssembler {
    /** Maximum ordinary working week in Peru (Constitución, art. 25). */
    private static final BigDecimal LEGAL_WEEKLY_HOURS = BigDecimal.valueOf(48);

    private final Clock clock;

    public WorkScheduleResourceAssembler(Clock clock) {
        this.clock = clock;
    }

    public WorkScheduleResource toResource(WorkSchedule workSchedule, Optional<Contract> contract) {
        var today = LocalDate.now(clock);
        var weeklyHours = workSchedule.weeklyHours();
        var contractResource = contract.map(item -> new AssociatedContractResource(item.getId(), item.getCode(),
                item.getModality(), item.getWeeklyHours(), item.getStartDate(), item.getEndDate(),
                item.viewStatus(today))).orElse(null);
        return new WorkScheduleResource(workSchedule.getId(), workSchedule.getCode(),
                workSchedule.getEmployeeId(), workSchedule.getBusinessId(), workSchedule.getName(),
                workSchedule.getStartDate(), workSchedule.getEndDate(), workSchedule.viewStatus(today),
                toShiftResources(workSchedule.getShifts()), workSchedule.workingDays(), weeklyHours,
                contractResource,
                WorkloadCompliance.of(weeklyHours, contract.map(Contract::getWeeklyHours).orElse(null)),
                weeklyHours.compareTo(LEGAL_WEEKLY_HOURS) > 0, workSchedule.getLastModifiedBy(),
                workSchedule.getCreatedAt(), workSchedule.getUpdatedAt());
    }

    public List<WorkShift> toShifts(List<WorkShiftResource> resources) {
        return resources.stream()
                .map(resource -> new WorkShift(resource.day(), resource.startTime(), resource.endTime()))
                .toList();
    }

    private static List<WorkShiftResource> toShiftResources(List<WorkShift> shifts) {
        return shifts.stream()
                .map(shift -> new WorkShiftResource(shift.day(), shift.startTime(), shift.endTime()))
                .toList();
    }
}
