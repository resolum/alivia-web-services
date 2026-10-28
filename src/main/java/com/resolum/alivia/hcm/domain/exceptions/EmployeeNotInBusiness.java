package com.resolum.alivia.hcm.domain.exceptions;

public class EmployeeNotInBusiness extends RuntimeException {
    public EmployeeNotInBusiness(Long employeeId, Long businessId) {
        super("Employee " + employeeId + " does not belong to business " + businessId + ".");
    }
}
