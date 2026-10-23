package com.resolum.alivia.subscriptions.domain.model.valueobjects;

import com.resolum.alivia.subscriptions.domain.exceptions.PlanNotFound;

import java.util.Locale;

public enum PlanCode {
    MINIMUM,
    CUSTOM;

    public static PlanCode from(String value) {
        if (value == null) {
            throw new PlanNotFound(value);
        }
        try {
            return PlanCode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new PlanNotFound(value);
        }
    }
}
