package com.resolum.alivia.shared.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

@Embeddable
public record EmployeeId(Long employeeId) {

    public EmployeeId {
        if (employeeId == null || employeeId <= 0) {
            throw new IllegalArgumentException("Employee ID must be a positive number.");
        }
    }
}
