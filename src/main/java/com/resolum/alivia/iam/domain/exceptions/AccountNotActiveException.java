package com.resolum.alivia.iam.domain.exceptions;

import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;

/**
 * Thrown when valid credentials belong to an account that cannot open a session.
 */
public class AccountNotActiveException extends RuntimeException {

    private final UserStatus status;

    /**
     * Creates the exception.
     *
     * @param status the current status of the account
     */
    public AccountNotActiveException(UserStatus status) {
        this.status = status;
    }

    /**
     * Returns the status that blocked the session.
     *
     * @return the account status
     */
    public UserStatus status() {
        return status;
    }
}
