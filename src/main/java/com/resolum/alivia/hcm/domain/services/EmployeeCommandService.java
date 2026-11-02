package com.resolum.alivia.hcm.domain.services;

import com.resolum.alivia.hcm.domain.model.aggregates.Employee;
import com.resolum.alivia.hcm.domain.model.commands.CreateEmployeeCommand;
import com.resolum.alivia.hcm.domain.model.commands.UpdateEmployeeCommand;

public interface EmployeeCommandService {
    Employee handle(CreateEmployeeCommand command);
    Employee handle(UpdateEmployeeCommand command);
}
