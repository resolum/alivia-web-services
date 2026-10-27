package com.resolum.alivia.iam.domain.model.commands;

/**
 * Command that requests a new email verification link.
 *
 * @param email the raw email address
 */
public record ResendVerificationCommand(String email) {
}
