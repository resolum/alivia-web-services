package com.resolum.alivia.hcm.domain.exceptions;

public class WorkScheduleNotEditable extends RuntimeException {
    public WorkScheduleNotEditable() {
        super("An inactive work schedule cannot be modified.");
    }
}
