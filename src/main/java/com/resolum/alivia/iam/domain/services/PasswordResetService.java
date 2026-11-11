package com.resolum.alivia.iam.domain.services;

import com.resolum.alivia.iam.domain.model.commands.ChangePasswordCommand;
import com.resolum.alivia.iam.domain.model.commands.ForgotPasswordCommand;
import com.resolum.alivia.iam.domain.model.commands.ResetPasswordCommand;

/** Password recovery with a 6-digit code sent by email, and password change for signed-in users. */
public interface PasswordResetService {
    void handle(ForgotPasswordCommand command);
    void handle(ResetPasswordCommand command);
    void handle(ChangePasswordCommand command);
}
