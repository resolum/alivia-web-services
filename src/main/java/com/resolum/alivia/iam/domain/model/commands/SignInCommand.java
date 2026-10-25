package com.resolum.alivia.iam.domain.model.commands;

/**
 * Command that requests a session with email and password.
 *
 * @param email    the raw email address
 * @param password the plain-text password
 */
public record SignInCommand(String email, String password) {
}
