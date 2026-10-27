package com.resolum.alivia.hcm.domain.model.queries;

import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.WorkScheduleViewStatus;
import org.springframework.data.domain.Pageable;

public record GetWorkSchedulesQuery(Long businessId, Long employeeId, Long contractId, EmployeeRole role,
                                    WorkScheduleViewStatus status, String search, Pageable pageable) {
}
