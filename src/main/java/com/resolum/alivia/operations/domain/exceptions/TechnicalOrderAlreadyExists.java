package com.resolum.alivia.operations.domain.exceptions;

import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;

public class TechnicalOrderAlreadyExists extends RuntimeException {
    public TechnicalOrderAlreadyExists(ServiceType serviceType) {
        super("This request already has a scheduled %s order.".formatted(serviceType.name().toLowerCase()));
    }
}
