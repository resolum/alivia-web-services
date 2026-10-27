package com.resolum.alivia.iam.domain.exceptions;

/**
 * Thrown when a sign-in attempt does not match any account. It never says which
 * credential was wrong.
 */
public class InvalidCredentialsException extends RuntimeException {
}
