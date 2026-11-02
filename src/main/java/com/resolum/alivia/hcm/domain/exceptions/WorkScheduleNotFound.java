package com.resolum.alivia.hcm.domain.exceptions;

public class WorkScheduleNotFound extends RuntimeException {
    public WorkScheduleNotFound(Long workScheduleId) {
        super("Work schedule " + workScheduleId + " was not found.");
    }
}
