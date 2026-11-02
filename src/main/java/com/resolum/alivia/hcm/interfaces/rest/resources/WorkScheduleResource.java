package com.resolum.alivia.hcm.interfaces.rest.resources;

import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleViewStatus;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkloadCompliance;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record WorkScheduleResource(Long id, String code, Long employeeId, Long businessId, String name,
        LocalDate startDate, LocalDate endDate, WorkScheduleViewStatus status,
        List<WorkShiftResource> shifts, Set<DayOfWeek> workingDays, BigDecimal weeklyHours,
        AssociatedContractResource contract, WorkloadCompliance compliance, boolean exceedsLegalLimit,
        Long lastModifiedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
