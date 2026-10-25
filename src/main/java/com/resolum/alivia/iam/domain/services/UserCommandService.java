package com.resolum.alivia.iam.domain.services;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.RefreshSessionCommand;
import com.resolum.alivia.iam.domain.model.commands.ResendVerificationCommand;
import com.resolum.alivia.iam.domain.model.commands.SignInCommand;
import com.resolum.alivia.iam.domain.model.commands.SignOutCommand;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.commands.VerifyEmailCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AuthSession;

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

    /**
     * Replaces the pending verification token and sends a new link.
     *
     * @param command the resend-verification command
     */
    void handle(ResendVerificationCommand command);

    /**
     * Authenticates an account and opens a session.
     *
     * @param command the sign-in command
     * @return the issued tokens
     */
    AuthSession handle(SignInCommand command);

    /**
     * Rotates the refresh token of a session and issues a new access token.
     *
     * @param command the refresh-session command
     * @return the issued tokens
     */
    AuthSession handle(RefreshSessionCommand command);

    /**
     * Closes the session bound to a refresh token. Unknown tokens are ignored.
     *
     * @param command the sign-out command
     */
    void handle(SignOutCommand command);
}
