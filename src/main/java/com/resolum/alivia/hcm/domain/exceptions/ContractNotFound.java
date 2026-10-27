package com.resolum.alivia.hcm.domain.exceptions;

public class ContractNotFound extends RuntimeException {
    public ContractNotFound(Long contractId) {
        super("Contract " + contractId + " was not found.");
    }
}
