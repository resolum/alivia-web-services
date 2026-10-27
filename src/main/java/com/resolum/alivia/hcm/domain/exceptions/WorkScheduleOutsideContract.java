package com.resolum.alivia.hcm.domain.exceptions;

public class WorkScheduleOutsideContract extends RuntimeException {
    public WorkScheduleOutsideContract(Long contractId) {
        super("The work schedule must fall within the period of contract " + contractId
                + ", which must be pending or active.");
    }
}
