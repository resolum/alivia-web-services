package com.resolum.alivia.subscriptions.domain.exceptions;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;

public class PlanNotFound extends RuntimeException {
    public PlanNotFound(PlanCode code) {
        this(String.valueOf(code));
    }

    public PlanNotFound(String code) {
        super("No se encontró un plan activo con código " + code + ".");
    }
}
