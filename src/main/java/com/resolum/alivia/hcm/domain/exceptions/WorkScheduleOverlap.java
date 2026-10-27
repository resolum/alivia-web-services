package com.resolum.alivia.hcm.domain.exceptions;

public class WorkScheduleOverlap extends RuntimeException {
    public WorkScheduleOverlap(Long employeeId) {
        super("Employee " + employeeId + " already has a work schedule in that period.");
    }
}
