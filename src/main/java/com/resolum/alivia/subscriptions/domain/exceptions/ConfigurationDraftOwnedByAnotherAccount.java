package com.resolum.alivia.subscriptions.domain.exceptions;

public class ConfigurationDraftOwnedByAnotherAccount extends RuntimeException {
    public ConfigurationDraftOwnedByAnotherAccount() {
        super("Esta solicitud pertenece a otra cuenta.");
    }
}
