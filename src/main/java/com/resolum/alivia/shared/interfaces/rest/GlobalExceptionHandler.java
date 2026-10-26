package com.resolum.alivia.shared.interfaces.rest;

import com.resolum.alivia.iam.domain.exceptions.AccountAlreadyVerifiedException;
import com.resolum.alivia.iam.domain.exceptions.AccountNotActiveException;
import com.resolum.alivia.iam.domain.exceptions.AccountNotFoundException;
import com.resolum.alivia.iam.domain.exceptions.AccountNotPendingVerificationException;
import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.InvalidCredentialsException;
import com.resolum.alivia.iam.domain.exceptions.InvalidRefreshTokenException;
import com.resolum.alivia.iam.domain.exceptions.InvalidVerificationTokenException;
import com.resolum.alivia.iam.domain.exceptions.RegistrationUnavailableException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.exceptions.VerificationResendTooSoonException;
import com.resolum.alivia.iam.domain.exceptions.VerificationTokenExpiredException;
import com.resolum.alivia.shared.infrastructure.web.CorrelationIdFilter;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidStripeWebhookSignature;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentRejected;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnauthorized;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnavailable;
import com.resolum.alivia.subscriptions.domain.exceptions.PlanNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.PreauthorizationConflict;
import com.resolum.alivia.subscriptions.domain.exceptions.PreauthorizationUnavailable;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookProcessingFailed;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookUnavailable;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERRORS_PROPERTY = "errors";
    private static final String CODE_PROPERTY = "code";
    private static final String CORRELATION_ID_PROPERTY = "correlationId";

    @ExceptionHandler(PreauthorizationConflict.class)
    public ProblemDetail handlePreauthorizationConflict(PreauthorizationConflict exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(PreauthorizationUnavailable.class)
    public ProblemDetail handlePreauthorizationUnavailable(PreauthorizationUnavailable exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(PaymentRejected.class)
    public ProblemDetail handlePaymentRejected(PaymentRejected exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setProperty("status", "PAYMENT_REJECTED");
        return problem;
    }

    @ExceptionHandler(StripeWebhookProcessingFailed.class)
    public ProblemDetail handleStripeWebhookProcessingFailed(StripeWebhookProcessingFailed exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    @ExceptionHandler(InvalidStripeWebhookSignature.class)
    public ProblemDetail handleInvalidStripeWebhookSignature(InvalidStripeWebhookSignature exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(StripeWebhookUnavailable.class)
    public ProblemDetail handleStripeWebhookUnavailable(StripeWebhookUnavailable exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(PaymentSetupNotFound.class)
    public ProblemDetail handlePaymentSetupNotFound(PaymentSetupNotFound exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PaymentSetupUnauthorized.class)
    public ProblemDetail handlePaymentSetupUnauthorized(PaymentSetupUnauthorized exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidArgument(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(PlanNotFound.class)
    public ProblemDetail handlePlanNotFound(PlanNotFound exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PaymentSetupUnavailable.class)
    public ProblemDetail handlePaymentSetupUnavailable(PaymentSetupUnavailable exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        return problem(HttpStatus.CONFLICT, "email_already_registered",
                "The email address is already registered.",
                List.of(new ApiFieldError("email", "email_already_registered")));
    }

    @ExceptionHandler(SignUpValidationException.class)
    public ProblemDetail handleSignUpValidation(SignUpValidationException exception) {
        var errors = exception.violations().stream()
                .map(violation -> new ApiFieldError(violation.field(), violation.code()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "validation_failed",
                "The request contains invalid fields.", errors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        Map<String, String> errorsByField = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errorsByField.putIfAbsent(fieldError.getField(), validationCode(fieldError));
        }
        var errors = errorsByField.entrySet().stream()
                .map(entry -> new ApiFieldError(entry.getKey(), entry.getValue()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "validation_failed",
                "The request contains invalid fields.", errors);
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    public ProblemDetail handleInvalidVerificationToken(InvalidVerificationTokenException exception) {
        return invalidVerificationTokenProblem();
    }

    // PENDING(contract): the error table has no code for blocked or already active accounts.
    @ExceptionHandler(AccountNotPendingVerificationException.class)
    public ProblemDetail handleAccountNotPendingVerification(
            AccountNotPendingVerificationException exception) {
        return invalidVerificationTokenProblem();
    }

    @ExceptionHandler(VerificationTokenExpiredException.class)
    public ProblemDetail handleVerificationTokenExpired(VerificationTokenExpiredException exception) {
        return problem(HttpStatus.BAD_REQUEST, "verification_token_expired",
                "The verification token has expired.", List.of());
    }

    @ExceptionHandler(AccountAlreadyVerifiedException.class)
    public ProblemDetail handleAccountAlreadyVerified(AccountAlreadyVerifiedException exception) {
        return problem(HttpStatus.CONFLICT, "account_already_verified",
                "The account has already been verified.", List.of());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "account_not_found",
                "The account does not exist.", List.of());
    }

    @ExceptionHandler(VerificationResendTooSoonException.class)
    public ResponseEntity<ProblemDetail> handleVerificationResendTooSoon(
            VerificationResendTooSoonException exception) {
        var problem = problem(HttpStatus.TOO_MANY_REQUESTS, "verification_resend_too_soon",
                "Wait before requesting another verification email.", List.of());
        problem.setProperty("retryAfterSeconds", exception.retryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "invalid_credentials",
                "The email or password is incorrect.", List.of());
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ProblemDetail handleAccountNotActive(AccountNotActiveException exception) {
        return problem(HttpStatus.FORBIDDEN, "account_not_active",
                "The account is not active.", List.of());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "refresh_token_invalid",
                "The refresh token is invalid or has expired.", List.of());
    }

    @ExceptionHandler(RegistrationUnavailableException.class)
    public ProblemDetail handleRegistrationUnavailable(RegistrationUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "registration_unavailable",
                "Registration is temporarily unavailable.", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied() {
        return problem(HttpStatus.FORBIDDEN, "access_denied",
                "The authenticated user is not allowed to perform this action.", List.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleInvalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "invalid_request",
                "The request body or parameters are invalid.", List.of());
    }

    /**
     * Safety net for unexpected failures. Framework exceptions that already carry their own
     * status (404, 405, 415...) are passed through so this handler does not turn them into 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode()).body(errorResponse.getBody());
        }
        log.error("Unhandled exception", exception);
        var problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "An unexpected error occurred.", List.of());
        var correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty(CORRELATION_ID_PROPERTY, correlationId);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private static ProblemDetail invalidVerificationTokenProblem() {
        return problem(HttpStatus.BAD_REQUEST, "verification_token_invalid",
                "The verification token is invalid or has already been used.", List.of());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail,
                                         List<ApiFieldError> errors) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE_PROPERTY, code);
        problem.setProperty(ERRORS_PROPERTY, errors);
        return problem;
    }

    private static String validationCode(FieldError fieldError) {
        String code = fieldError.getCode();
        if ("AssertTrue".equals(code)) {
            return "terms_not_accepted";
        }
        if ("Size".equals(code) || "Length".equals(code)) {
            return "too_long";
        }
        if ("NotBlank".equals(code) || "NotNull".equals(code) || "NotEmpty".equals(code)) {
            return "required";
        }
        return "invalid";
    }

    public record ApiFieldError(String field, String code) {
    }
}
