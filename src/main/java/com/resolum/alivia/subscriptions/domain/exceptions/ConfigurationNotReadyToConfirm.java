package com.resolum.alivia.subscriptions.domain.exceptions;

public class ConfigurationNotReadyToConfirm extends RuntimeException {
    public ConfigurationNotReadyToConfirm() {
        super("La configuración solo se confirma cuando el técnico validó el hogar en la evaluación.");
    }
}
