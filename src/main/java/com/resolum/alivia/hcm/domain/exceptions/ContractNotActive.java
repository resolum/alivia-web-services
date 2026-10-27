package com.resolum.alivia.hcm.domain.exceptions;

public class ContractNotActive extends RuntimeException {
    public ContractNotActive() {
        super("A terminated contract cannot be modified.");
    }
}
