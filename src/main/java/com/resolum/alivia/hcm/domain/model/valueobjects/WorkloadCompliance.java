package com.resolum.alivia.hcm.domain.model.valueobjects;

import java.math.BigDecimal;

public enum WorkloadCompliance {
    COMPLIANT,
    UNDER,
    OVER,
    NO_REFERENCE;

    public static WorkloadCompliance of(BigDecimal configuredHours, Integer contractedHours) {
        if (contractedHours == null) return NO_REFERENCE;
        int comparison = configuredHours.compareTo(BigDecimal.valueOf(contractedHours));
        if (comparison == 0) return COMPLIANT;
        return comparison < 0 ? UNDER : OVER;
    }
}
