package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.InvalidVerificationTokenException;
import com.resolum.alivia.iam.domain.exceptions.RegistrationUnavailableException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.exceptions.VerificationTokenExpiredException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.commands.VerifyEmailCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.iam.interfaces.rest.controllers.AuthenticationController;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationControllerTest {

    private static final String SIGN_UP_PATH = "/api/v1/auth/sign-up";
    private static final String VERIFY_EMAIL_PATH = "/api/v1/auth/verify-email";
    private static final String VALID_SIGN_UP_BODY = """
            {
              "fullName": "Maria Lopez",
              "email": "maria.lopez@example.com",
              "phoneNumber": "+51987654321",
              "password": "Strong1!",
              "acceptedTerms": true
            }
            """;

    private final UserCommandService commandService = mock(UserCommandService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AuthenticationController(commandService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void signUpReturnsCreatedWithMaskedEmail() throws Exception {
        var user = user(7L, "maria.lopez@example.com", UserStatus.PENDING_VERIFICATION);
        when(commandService.handle(any(SignUpCommand.class))).thenReturn(user);

        mvc.perform(post(SIGN_UP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SIGN_UP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(7))
                .andExpect(jsonPath("$.maskedEmail").value("m***@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

        verify(commandService).handle(any(SignUpCommand.class));
    }

    @Test
    void signUpReturnsConflictWhenEmailIsRegistered() throws Exception {
        when(commandService.handle(any(SignUpCommand.class)))
                .thenThrow(new EmailAlreadyRegisteredException());

        mvc.perform(post(SIGN_UP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SIGN_UP_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_already_registered"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("email_already_registered"));
    }

    @Test
    void signUpReturnsPasswordValidationError() throws Exception {
        when(commandService.handle(any(SignUpCommand.class)))
                .thenThrow(new SignUpValidationException("password",
                        SignUpValidationException.PASSWORD_WEAK));

        mvc.perform(post(SIGN_UP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SIGN_UP_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value("password_weak"));
    }

    @Test
    void signUpRejectsUnacceptedTerms() throws Exception {
        var body = VALID_SIGN_UP_BODY.replace("\"acceptedTerms\": true",
                "\"acceptedTerms\": false");

        mvc.perform(post(SIGN_UP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors[0].field").value("acceptedTerms"))
                .andExpect(jsonPath("$.errors[0].code").value("terms_not_accepted"));
    }

    @Test
    void signUpReportsOneRequiredErrorPerMissingField() throws Exception {
        mvc.perform(post(SIGN_UP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.length()").value(5))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].code").value("required"));
    }

    @Test
    void signUpReturnsServiceUnavailableWhenRegistrationDependencyFails() throws Exception {
        when(commandService.handle(any(SignUpCommand.class)))
                .thenThrow(new RegistrationUnavailableException(new IllegalStateException("down")));

        mvc.perform(post(SIGN_UP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SIGN_UP_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("registration_unavailable"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void verifyEmailReturnsActiveAccount() throws Exception {
        var user = user(7L, "maria.lopez@example.com", UserStatus.ACTIVE);
        when(commandService.handle(any(VerifyEmailCommand.class))).thenReturn(user);

        mvc.perform(post(VERIFY_EMAIL_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"raw-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(7))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void verifyEmailReturnsInvalidTokenError() throws Exception {
        when(commandService.handle(any(VerifyEmailCommand.class)))
                .thenThrow(new InvalidVerificationTokenException());

        mvc.perform(post(VERIFY_EMAIL_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"invented\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("verification_token_invalid"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void verifyEmailReturnsExpiredTokenError() throws Exception {
        when(commandService.handle(any(VerifyEmailCommand.class)))
                .thenThrow(new VerificationTokenExpiredException());

        mvc.perform(post(VERIFY_EMAIL_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"expired\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("verification_token_expired"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    private static User user(Long id, String email, UserStatus status) {
        var user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(new EmailAddress(email));
        when(user.getStatus()).thenReturn(status);
        return user;
    }
}
