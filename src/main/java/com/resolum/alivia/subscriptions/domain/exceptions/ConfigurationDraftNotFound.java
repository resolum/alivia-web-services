package com.resolum.alivia.subscriptions.domain.exceptions;

public class ConfigurationDraftNotFound extends RuntimeException {
    public ConfigurationDraftNotFound(String id) {
        super("No existe el borrador de configuración " + id + ".");
    }
}
