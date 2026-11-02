package com.resolum.alivia.iam.domain.exceptions;

/**
 * Thrown when a verification token is missing, incorrect, belongs to another account
 * or has already been used.
 */
public class InvalidVerificationTokenException extends RuntimeException {
}
