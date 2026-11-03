package com.resolum.alivia.subscriptions.domain.model.commands;

/** The family confirms the configuration that the technician validated at the home. */
public record ConfirmConfigurationCommand(String draftId) {
}
