package com.resolum.alivia.hcm.domain.exceptions;

public class ContractOverlap extends RuntimeException {
    public ContractOverlap(Long employeeId) {
        super("Employee " + employeeId + " already has an incompatible contract in that period.");
    }
}
