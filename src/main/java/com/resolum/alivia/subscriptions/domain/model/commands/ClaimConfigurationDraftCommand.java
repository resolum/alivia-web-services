package com.resolum.alivia.subscriptions.domain.model.commands;

/** Links a request built before sign-in to the family's account. */
public record ClaimConfigurationDraftCommand(String draftId, Long userId) {
}
