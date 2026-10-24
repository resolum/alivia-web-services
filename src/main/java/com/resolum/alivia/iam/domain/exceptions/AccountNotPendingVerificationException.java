package com.resolum.alivia.iam.domain.exceptions;

/**
 * Thrown when an email verification is attempted for an account that is not pending
 * verification.
 */
public class AccountNotPendingVerificationException extends RuntimeException {
}
