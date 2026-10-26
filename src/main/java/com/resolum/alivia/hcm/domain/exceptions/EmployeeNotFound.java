package com.resolum.alivia.hcm.domain.exceptions;

public class EmployeeNotFound extends RuntimeException {
    public EmployeeNotFound(Long employeeId) {
        super("Employee " + employeeId + " was not found.");
    }
}
