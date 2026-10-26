package com.resolum.alivia.hcm.domain.services;

import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.queries.GetEmployeeByIdQuery;
import com.resolum.alivia.hcm.domain.model.queries.GetEmployeesQuery;
import org.springframework.data.domain.Page;

public interface EmployeeQueryService {
    Page<Employee> handle(GetEmployeesQuery query);
    Employee handle(GetEmployeeByIdQuery query);
}
