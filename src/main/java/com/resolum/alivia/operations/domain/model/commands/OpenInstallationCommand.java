package com.resolum.alivia.operations.domain.model.commands;

/**
 * Opens the installation order of a request whose family confirmed the configuration after a viable evaluation.
 *
 * @param draftId the configuration draft of the request
 */
public record OpenInstallationCommand(String draftId) {
}
