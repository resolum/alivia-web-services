package com.resolum.alivia.operations.domain.model.commands;

/** The family gives the held window back, so other families see it again. */
public record ReleaseEvaluationSlotCommand(String draftId) {
}
