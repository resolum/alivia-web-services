package com.resolum.alivia.hcm.domain.model.queries;

import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeRole;
import com.resolum.alivia.hcm.domain.model.valueobjects.EmployeeStatus;
import org.springframework.data.domain.Pageable;

public record GetEmployeesQuery(Long businessId, EmployeeStatus status, EmployeeRole role,
                                Pageable pageable) {
}
