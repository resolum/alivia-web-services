package com.resolum.alivia.communications.domain.model.valueobjects;

import java.util.Collections;
import java.util.Set;

public enum EmailTemplate {
    VERIFICATION_CODE,
    CORPORATE_ACCOUNT,
    RESET_PASSWORD,
    CAREGIVER_INVITATION,
    SUBSCRIPTION_WELCOME,
    PAYMENT_FAILED,
    SUBSCRIPTION_REFUNDED,
    INSTALLATION_REJECTED,
    INSTALLATION_CANCELED,
    EMPLOYEE_SUSPENDED,
    EMPLOYEE_REINSTATED,
    EMPLOYEE_TERMINATED;

    public Set<String> requiredVariables() {
        return switch (this) {
            case VERIFICATION_CODE -> Set.of("code");
            case CORPORATE_ACCOUNT -> Set.of("temporaryPassword");
            case RESET_PASSWORD -> Set.of("resetUrl");
            case CAREGIVER_INVITATION -> Set.of("passwordSetupUrl");
            case SUBSCRIPTION_WELCOME -> Set.of("plan");
            case PAYMENT_FAILED -> Set.of("failureMessage");
            case SUBSCRIPTION_REFUNDED -> Set.of("amount");
            case INSTALLATION_REJECTED,
                 INSTALLATION_CANCELED,
                 EMPLOYEE_SUSPENDED,
                 EMPLOYEE_REINSTATED,
                 EMPLOYEE_TERMINATED -> Collections.emptySet();
        };
    }
}
