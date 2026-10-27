package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.exceptions.AccountAlreadyVerifiedException;
import com.resolum.alivia.iam.domain.exceptions.AccountNotActiveException;
import com.resolum.alivia.iam.domain.exceptions.VerificationResendTooSoonException;
import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.InvalidCredentialsException;
import com.resolum.alivia.iam.domain.exceptions.InvalidRefreshTokenException;
import com.resolum.alivia.iam.domain.exceptions.InvalidVerificationTokenException;
import com.resolum.alivia.iam.domain.exceptions.RegistrationUnavailableException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.exceptions.VerificationTokenExpiredException;
import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.commands.RefreshSessionCommand;
import com.resolum.alivia.iam.domain.model.commands.ResendVerificationCommand;
import com.resolum.alivia.iam.domain.model.commands.SignInCommand;
import com.resolum.alivia.iam.domain.model.commands.SignOutCommand;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.commands.VerifyEmailCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.AuthSession;
import com.resolum.alivia.iam.domain.model.valueobjects.UserStatus;
import com.resolum.alivia.iam.domain.model.queries.GetUserByAccountIdQuery;
import com.resolum.alivia.iam.domain.services.UserCommandService;
import com.resolum.alivia.iam.domain.services.UserQueryService;
import com.resolum.alivia.iam.interfaces.rest.controllers.AuthenticationController;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationControllerTest {

        private static final String SIGN_UP_PATH = "/api/v1/auth/sign-up";
        private static final String VERIFY_EMAIL_PATH = "/api/v1/auth/verify-email";
        private static final String LOGIN_PATH = "/api/v1/auth/sign-in";
        private static final String REFRESH_PATH = "/api/v1/auth/refresh";
        private static final String LOGOUT_PATH = "/api/v1/auth/logout";
        private static final String RESEND_VERIFICATION_PATH = "/api/v1/auth/resend-verification";
        private static final String VERIFICATION_STATUS_PATH = "/api/v1/auth/accounts/{accountId}/verification-status";
        private static final String RESEND_BODY = """
                        {"email": "maria.lopez@example.com"}
                        """;
        private static final String LOGIN_BODY = """
                        {"email": "maria.lopez@example.com", "password": "Strong1!"}
                        """;
        private static final String REFRESH_BODY = """
                        {"refreshToken": "refresh-token"}
                        """;
        private static final Map<String, String> VALID_SIGN_UP_FORM = Map.of(
                        "firstName", "Maria",
                        "lastName", "Lopez",
                        "email", "maria.lopez@example.com",
                        "phoneNumber", "+51987654321",
                        "password", "Strong1!",
                        "acceptedTerms", "true");

        private final UserCommandService commandService = mock(UserCommandService.class);
        private final UserQueryService queryService = mock(UserQueryService.class);
        private final MockMvc mvc = MockMvcBuilders
                        .standaloneSetup(new AuthenticationController(commandService, queryService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        @Test
        void signUpReturnsCreatedWithMaskedEmail() throws Exception {
                var user = user(7L, "maria.lopez@example.com", UserStatus.PENDING_VERIFICATION);
                when(commandService.handle(any(SignUpCommand.class))).thenReturn(user);

                mvc.perform(signUp())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.accountId").value("acc-7"))
                                .andExpect(jsonPath("$.maskedEmail").value("m***@example.com"))
                                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

                verify(commandService).handle(any(SignUpCommand.class));
        }

        @Test
        void signUpReturnsConflictWhenEmailIsRegistered() throws Exception {
                when(commandService.handle(any(SignUpCommand.class)))
                                .thenThrow(new EmailAlreadyRegisteredException());

                mvc.perform(signUp())
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

                mvc.perform(signUp())
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("validation_failed"))
                                .andExpect(jsonPath("$.errors[0].field").value("password"))
                                .andExpect(jsonPath("$.errors[0].code").value("password_weak"));
        }

        @Test
        void signUpRejectsUnacceptedTerms() throws Exception {
                mvc.perform(signUp("acceptedTerms", "false"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("validation_failed"))
                                .andExpect(jsonPath("$.errors[0].field").value("acceptedTerms"))
                                .andExpect(jsonPath("$.errors[0].code").value("terms_not_accepted"));
        }

        @Test
        void signUpReportsOneRequiredErrorPerMissingField() throws Exception {
                mvc.perform(multipart(SIGN_UP_PATH))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("validation_failed"))
                                .andExpect(jsonPath("$.errors.length()").value(6))
                                .andExpect(jsonPath("$.errors[?(@.field == 'email')].code").value("required"));
        }

        @Test
        void signUpRejectsMalformedDni() throws Exception {
                mvc.perform(signUp("dni", "123"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("validation_failed"))
                                .andExpect(jsonPath("$.errors.length()").value(1))
                                .andExpect(jsonPath("$.errors[0].field").value("dni"));
        }

        @Test
        void signUpRejectsPhotoThatIsNotAnImage() throws Exception {
                var file = new MockMultipartFile("photo", "notes.txt", "text/plain", "hello".getBytes());

                mvc.perform(signUp().file(file))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("validation_failed"))
                                .andExpect(jsonPath("$.errors[0].field").value("photo"))
                                .andExpect(jsonPath("$.errors[0].code").value("invalid_photo"));
        }

        @Test
        void signUpForwardsUploadedPhotoToCommand() throws Exception {
                var user = user(7L, "maria.lopez@example.com", UserStatus.PENDING_VERIFICATION);
                when(commandService.handle(any(SignUpCommand.class))).thenReturn(user);
                byte[] image = {1, 2, 3};

                mvc.perform(signUp().file(new MockMultipartFile("photo", "maria.jpg", "image/jpeg", image)))
                                .andExpect(status().isCreated());

                verify(commandService).handle(argThat((SignUpCommand command) -> Arrays.equals(image, command.photo())
                                && "maria.jpg".equals(command.photoFileName())));
        }

        @Test
        void signUpReturnsServiceUnavailableWhenRegistrationDependencyFails() throws Exception {
                when(commandService.handle(any(SignUpCommand.class)))
                                .thenThrow(new RegistrationUnavailableException(new IllegalStateException("down")));

                mvc.perform(signUp())
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
                                .andExpect(jsonPath("$.accountId").value("acc-7"))
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

        @Test
        void signUpWithDifferentRoles() throws Exception {
                var user = user(8L, "employee@example.com", UserStatus.PENDING_VERIFICATION);
                when(commandService.handle(any(SignUpCommand.class))).thenReturn(user);

                mvc.perform(signUp("firstName", "Juan", "lastName", "Perez", "email", "employee@example.com",
                                "password", "StrongPassword1!", "role", "EMPLOYEE", "businessId", "10"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.accountId").value("acc-8"))
                                .andExpect(jsonPath("$.maskedEmail").value("e***@example.com"))
                                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

                verify(commandService).handle(any(SignUpCommand.class));
        }

        @Test
        void loginReturnsTokensUserAndPermissions() throws Exception {
                var user = user(7L, "maria.lopez@example.com", UserStatus.ACTIVE);
                when(user.getAccessRole()).thenReturn(AccessRole.CAREGIVER);
                when(commandService.handle(any(SignInCommand.class)))
                                .thenReturn(new AuthSession(user, "access-token", "refresh-token", 900L));

                mvc.perform(post(LOGIN_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(LOGIN_BODY))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").value("access-token"))
                                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                                .andExpect(jsonPath("$.expiresIn").value(900))
                                .andExpect(jsonPath("$.user.id").value("acc-7"))
                                .andExpect(jsonPath("$.user.email").value("maria.lopez@example.com"))
                                .andExpect(jsonPath("$.user.role").value("CAREGIVER"))
                                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                                .andExpect(jsonPath("$.permissions[0]").value("ROLE_CAREGIVER"));
        }

        @Test
        void loginReturnsUnauthorizedWithoutNamingTheWrongCredential() throws Exception {
                when(commandService.handle(any(SignInCommand.class))).thenThrow(new InvalidCredentialsException());

                mvc.perform(post(LOGIN_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(LOGIN_BODY))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.code").value("invalid_credentials"))
                                .andExpect(jsonPath("$.errors").isEmpty());
        }

        @Test
        void loginReturnsForbiddenForBlockedAndInactiveAccounts() throws Exception {
                when(commandService.handle(any(SignInCommand.class)))
                                .thenThrow(new AccountNotActiveException(UserStatus.INACTIVE))
                                .thenThrow(new AccountNotActiveException(UserStatus.BLOCKED));

                mvc.perform(post(LOGIN_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(LOGIN_BODY))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").value("account_not_active"));
                mvc.perform(post(LOGIN_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(LOGIN_BODY))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").value("account_not_active"));
        }

        @Test
        void loginRejectsBlankFields() throws Exception {
                mvc.perform(post(LOGIN_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"\",\"password\":\"\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("validation_failed"));
        }

        @Test
        void refreshReturnsNewTokensOrUnauthorized() throws Exception {
                var user = user(7L, "maria.lopez@example.com", UserStatus.ACTIVE);
                when(user.getAccessRole()).thenReturn(AccessRole.CAREGIVER);
                when(commandService.handle(any(RefreshSessionCommand.class)))
                                .thenReturn(new AuthSession(user, "new-access", "new-refresh", 900L))
                                .thenThrow(new InvalidRefreshTokenException());

                mvc.perform(post(REFRESH_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REFRESH_BODY))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").value("new-access"))
                                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
                mvc.perform(post(REFRESH_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REFRESH_BODY))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.code").value("refresh_token_invalid"));
        }

        @Test
        void logoutReturnsNoContent() throws Exception {
                mvc.perform(post(LOGOUT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REFRESH_BODY))
                                .andExpect(status().isNoContent());

                verify(commandService).handle(any(SignOutCommand.class));
        }

        @Test
        void resendVerificationReturnsAccepted() throws Exception {
                mvc.perform(post(RESEND_VERIFICATION_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(RESEND_BODY))
                                .andExpect(status().isAccepted());

                verify(commandService).handle(new ResendVerificationCommand("maria.lopez@example.com"));
        }

        @Test
        void resendVerificationMapsCooldownActiveAccountAndBlankEmail() throws Exception {
                doThrow(new VerificationResendTooSoonException(42))
                                .doThrow(new AccountAlreadyVerifiedException())
                                .when(commandService).handle(any(ResendVerificationCommand.class));

                mvc.perform(post(RESEND_VERIFICATION_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(RESEND_BODY))
                                .andExpect(status().isTooManyRequests())
                                .andExpect(header().string("Retry-After", "42"))
                                .andExpect(jsonPath("$.code").value("verification_resend_too_soon"))
                                .andExpect(jsonPath("$.retryAfterSeconds").value(42));
                mvc.perform(post(RESEND_VERIFICATION_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(RESEND_BODY))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("account_already_verified"));
                mvc.perform(post(RESEND_VERIFICATION_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \" \"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("validation_failed"));
        }

        @Test
        void verificationStatusReturnsFlagOrNotFound() throws Exception {
                var user = user(7L, "maria.lopez@example.com", UserStatus.PENDING_VERIFICATION);
                when(user.getEmailVerified()).thenReturn(false);
                when(queryService.handle(new GetUserByAccountIdQuery("acc-7"))).thenReturn(Optional.of(user));
                when(queryService.handle(new GetUserByAccountIdQuery("missing"))).thenReturn(Optional.empty());

                mvc.perform(get(VERIFICATION_STATUS_PATH, "acc-7"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountId").value("acc-7"))
                                .andExpect(jsonPath("$.emailVerified").value(false))
                                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
                mvc.perform(get(VERIFICATION_STATUS_PATH, "missing"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("account_not_found"));
        }

        private static User user(Long id, String email, UserStatus status) {
                var user = mock(User.class);
                when(user.getId()).thenReturn(id);
                when(user.getAccountId()).thenReturn("acc-" + id);
                when(user.getEmail()).thenReturn(new EmailAddress(email));
                when(user.getStatus()).thenReturn(status);
                return user;
        }

        // Valid sign-up form; pairs of name and value replace its fields.
        private static MockMultipartHttpServletRequestBuilder signUp(String... overrides) {
                var form = new HashMap<>(VALID_SIGN_UP_FORM);
                for (int i = 0; i < overrides.length; i += 2) {
                        form.put(overrides[i], overrides[i + 1]);
                }
                var request = multipart(SIGN_UP_PATH);
                form.forEach(request::param);
                return request;
        }
}
