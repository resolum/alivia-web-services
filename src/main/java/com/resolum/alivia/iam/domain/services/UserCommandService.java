package com.resolum.alivia.iam.domain.services;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.commands.VerifyEmailCommand;

/**
 * Command service of the IAM bounded context.
 */
public interface UserCommandService {

    /**
     * Registers a new account and requests its email verification.
     *
     * @param command the sign-up command
     * @return the registered user
     */
    User handle(SignUpCommand command);

    /**
     * Verifies the email address of an account.
     *
     * @param command the verify-email command
     * @return the verified user
     */
    User handle(VerifyEmailCommand command);
}
