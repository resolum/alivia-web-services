package com.resolum.alivia.shared.interfaces.rest;

import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.hcm.domain.exceptions.ContractNotActive;
import com.resolum.alivia.hcm.domain.exceptions.ContractNotFound;
import com.resolum.alivia.hcm.domain.exceptions.ContractOverlap;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleNotEditable;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleNotFound;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleOutsideContract;
import com.resolum.alivia.hcm.domain.exceptions.WorkScheduleOverlap;
import com.resolum.alivia.business.domain.exceptions.AdministratorNotFound;
import com.resolum.alivia.business.domain.exceptions.BackupPersonalEmailAlreadyExists;
import com.resolum.alivia.business.domain.exceptions.BusinessAlreadyExistsForAdministrator;
import com.resolum.alivia.business.domain.exceptions.BusinessNameAlreadyExists;
import com.resolum.alivia.business.domain.exceptions.BusinessNotFound;
import com.resolum.alivia.business.domain.exceptions.DniAlreadyExists;
import com.resolum.alivia.business.domain.exceptions.EmailAddressAlreadyExists;
import com.resolum.alivia.business.domain.exceptions.LegalNameAlreadyExists;
import com.resolum.alivia.business.domain.exceptions.RucAlreadyExists;
import com.resolum.alivia.business.domain.exceptions.UnauthorizedAdministratorException;
import com.resolum.alivia.iam.domain.exceptions.AccountAlreadyVerifiedException;
import com.resolum.alivia.iam.domain.exceptions.AccountNotActiveException;
import com.resolum.alivia.iam.domain.exceptions.AccountNotFoundException;
import com.resolum.alivia.iam.domain.exceptions.AccountNotPendingVerificationException;
import com.resolum.alivia.iam.domain.exceptions.DniAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.resolum.alivia.iam.domain.exceptions.InvalidCredentialsException;
import com.resolum.alivia.iam.domain.exceptions.InvalidRefreshTokenException;
import com.resolum.alivia.iam.domain.exceptions.InvalidVerificationTokenException;
import com.resolum.alivia.iam.domain.exceptions.RegistrationUnavailableException;
import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.exceptions.VerificationResendTooSoonException;
import com.resolum.alivia.iam.domain.exceptions.VerificationTokenExpiredException;
import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationDraftNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidStripeWebhookSignature;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentRejected;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnavailable;
import com.resolum.alivia.subscriptions.domain.exceptions.PlanNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.PreauthorizationUnavailable;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookProcessingFailed;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookUnavailable;
import com.resolum.alivia.profiles.domain.exceptions.BusinessNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.BusinessProfileNotFoundException;
import com.resolum.alivia.profiles.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.LegalNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.PersonProfileNotFoundException;
import com.resolum.alivia.profiles.domain.exceptions.RucAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.StaffProfileNotFoundException;
import com.resolum.alivia.shared.infrastructure.web.CorrelationIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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

    @ExceptionHandler(ContractNotFound.class)
    public ProblemDetail handleContractNotFound(ContractNotFound exception) {
        return problem(HttpStatus.NOT_FOUND, "contract_not_found", exception.getMessage(), List.of());
    }

    @ExceptionHandler(ContractOverlap.class)
    public ProblemDetail handleContractOverlap(ContractOverlap exception) {
        return problem(HttpStatus.CONFLICT, "contract_overlap", exception.getMessage(), List.of());
    }

    @ExceptionHandler(ContractNotActive.class)
    public ProblemDetail handleContractNotActive(ContractNotActive exception) {
        return problem(HttpStatus.CONFLICT, "contract_not_active", exception.getMessage(), List.of());
    }

    @ExceptionHandler(WorkScheduleNotFound.class)
    public ProblemDetail handleWorkScheduleNotFound(WorkScheduleNotFound exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({WorkScheduleOverlap.class, WorkScheduleOutsideContract.class,
            WorkScheduleNotEditable.class})
    public ProblemDetail handleWorkScheduleConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ConfigurationDraftNotFound.class)
    public ProblemDetail handleConfigurationDraftNotFound(ConfigurationDraftNotFound exception) {
        return problem(HttpStatus.NOT_FOUND, "configuration_draft_not_found",
                exception.getMessage(), List.of());
    }

    @ExceptionHandler(EmployeeNotFound.class)
    public ProblemDetail handleEmployeeNotFound(EmployeeNotFound exception) {
        return problem(HttpStatus.NOT_FOUND, "employee_not_found",
                "The employee does not exist.", List.of());
    }

    @ExceptionHandler(StaffProfileNotFoundException.class)
    public ProblemDetail handleStaffProfileNotFound(StaffProfileNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "staff_profile_not_found",
                "The staff profile does not exist.", List.of());
    }

    @ExceptionHandler(PersonProfileNotFoundException.class)
    public ProblemDetail handlePersonProfileNotFound(PersonProfileNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "person_profile_not_found",
                "The person profile does not exist.", List.of());
    }

    @ExceptionHandler(BusinessProfileNotFoundException.class)
    public ProblemDetail handleBusinessProfileNotFound(BusinessProfileNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "business_profile_not_found",
                "The business profile does not exist.", List.of());
    }

    @ExceptionHandler(EmployeeNotInBusiness.class)
    public ProblemDetail handleEmployeeNotInBusiness(EmployeeNotInBusiness exception) {
        return problem(HttpStatus.FORBIDDEN, "employee_not_in_business",
                exception.getMessage(), List.of());
    }

    @ExceptionHandler(PreauthorizationUnavailable.class)
    public ProblemDetail handlePreauthorizationUnavailable(PreauthorizationUnavailable exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "preauthorization_unavailable",
                exception.getMessage(), List.of());
    }

    @ExceptionHandler(PaymentRejected.class)
    public ProblemDetail handlePaymentRejected(PaymentRejected exception) {
        var problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "payment_rejected",
                exception.getMessage(), List.of());
        problem.setProperty("status", "PAYMENT_REJECTED");
        return problem;
    }

    @ExceptionHandler(StripeWebhookProcessingFailed.class)
    public ProblemDetail handleStripeWebhookProcessingFailed(StripeWebhookProcessingFailed exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "stripe_webhook_processing_failed",
                exception.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidStripeWebhookSignature.class)
    public ProblemDetail handleInvalidStripeWebhookSignature(InvalidStripeWebhookSignature exception) {
        return problem(HttpStatus.BAD_REQUEST, "invalid_stripe_webhook_signature",
                exception.getMessage(), List.of());
    }

    @ExceptionHandler(StripeWebhookUnavailable.class)
    public ProblemDetail handleStripeWebhookUnavailable(StripeWebhookUnavailable exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "stripe_webhook_unavailable",
                exception.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "invalid_argument",
                exception.getMessage(), List.of());
    }

    @ExceptionHandler(PlanNotFound.class)
    public ProblemDetail handlePlanNotFound(PlanNotFound exception) {
        return problem(HttpStatus.NOT_FOUND, "plan_not_found", exception.getMessage(), List.of());
    }

    @ExceptionHandler(PaymentSetupUnavailable.class)
    public ProblemDetail handlePaymentSetupUnavailable(PaymentSetupUnavailable exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "payment_setup_unavailable",
                exception.getMessage(), List.of());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        return problem(HttpStatus.CONFLICT, "email_already_registered",
                "The email address is already registered.",
                List.of(new ApiFieldError("email", "email_already_registered")));
    }

    @ExceptionHandler(DniAlreadyRegisteredException.class)
    public ProblemDetail handleDniAlreadyRegistered(DniAlreadyRegisteredException exception) {
        return problem(HttpStatus.CONFLICT, "dni_already_registered",
                "The DNI is already registered.",
                List.of(new ApiFieldError("dni", "dni_already_registered")));
    }

    @ExceptionHandler(EmailAddressAlreadyExistsException.class)
    public ProblemDetail handleEmailAddressAlreadyExists(EmailAddressAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "email_already_registered",
                "The email address is already registered.", List.of());
    }

    @ExceptionHandler(DniAlreadyExistsException.class)
    public ProblemDetail handleDniAlreadyExists(DniAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "dni_already_registered",
                "The DNI is already registered.", List.of());
    }

    @ExceptionHandler(RucAlreadyExistsException.class)
    public ProblemDetail handleRucAlreadyExists(RucAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "ruc_already_exists",
                "The RUC is already registered.", List.of());
    }

    @ExceptionHandler(BusinessNameAlreadyExistsException.class)
    public ProblemDetail handleBusinessNameAlreadyExists(BusinessNameAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "business_name_already_exists",
                "The business name is already registered.", List.of());
    }

    @ExceptionHandler(LegalNameAlreadyExistsException.class)
    public ProblemDetail handleLegalNameAlreadyExists(LegalNameAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "legal_name_already_exists",
                "The legal name is already registered.", List.of());
    }

    @ExceptionHandler(BusinessNotFound.class)
    public ProblemDetail handleBusinessNotFound(BusinessNotFound exception) {
        return problem(HttpStatus.NOT_FOUND, "business_not_found", exception.getMessage(), List.of());
    }

    @ExceptionHandler(AdministratorNotFound.class)
    public ProblemDetail handleAdministratorNotFound(AdministratorNotFound exception) {
        return problem(HttpStatus.NOT_FOUND, "administrator_not_found", exception.getMessage(), List.of());
    }

    @ExceptionHandler(BusinessAlreadyExistsForAdministrator.class)
    public ProblemDetail handleBusinessAlreadyExistsForAdministrator(BusinessAlreadyExistsForAdministrator exception) {
        return problem(HttpStatus.CONFLICT, "business_already_exists_for_administrator", exception.getMessage(), List.of());
    }

    @ExceptionHandler(DniAlreadyExists.class)
    public ProblemDetail handleDniAlreadyExists(DniAlreadyExists exception) {
        return problem(HttpStatus.CONFLICT, "dni_already_registered", exception.getMessage(), List.of());
    }

    @ExceptionHandler(EmailAddressAlreadyExists.class)
    public ProblemDetail handleEmailAddressAlreadyExists(EmailAddressAlreadyExists exception) {
        return problem(HttpStatus.CONFLICT, "email_already_registered", exception.getMessage(), List.of());
    }

    @ExceptionHandler(BusinessNameAlreadyExists.class)
    public ProblemDetail handleBusinessNameAlreadyExists(BusinessNameAlreadyExists exception) {
        return problem(HttpStatus.CONFLICT, "business_name_already_exists", exception.getMessage(), List.of());
    }

    @ExceptionHandler(LegalNameAlreadyExists.class)
    public ProblemDetail handleLegalNameAlreadyExists(LegalNameAlreadyExists exception) {
        return problem(HttpStatus.CONFLICT, "legal_name_already_exists", exception.getMessage(), List.of());
    }

    @ExceptionHandler(RucAlreadyExists.class)
    public ProblemDetail handleRucAlreadyExists(RucAlreadyExists exception) {
        return problem(HttpStatus.CONFLICT, "ruc_already_exists", exception.getMessage(), List.of());
    }

    @ExceptionHandler(BackupPersonalEmailAlreadyExists.class)
    public ProblemDetail handleBackupPersonalEmailAlreadyExists(BackupPersonalEmailAlreadyExists exception) {
        return problem(HttpStatus.CONFLICT, "backup_personal_email_already_exists", exception.getMessage(), List.of());
    }

    @ExceptionHandler(UnauthorizedAdministratorException.class)
    public ProblemDetail handleUnauthorizedAdministrator(UnauthorizedAdministratorException exception) {
        return problem(HttpStatus.FORBIDDEN, "unauthorized_administrator", exception.getMessage(), List.of());
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
     * status (404, 405, 415...) keep that status and body and only receive the shared
     * {@code code} and {@code errors} properties, so this handler does not turn them into 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            var status = errorResponse.getStatusCode();
            var problem = errorResponse.getBody();
            if (problem == null) {
                problem = ProblemDetail.forStatus(status);
            }
            enrichFrameworkProblem(problem, status);
            return ResponseEntity.status(status).body(problem);
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

    /**
     * Adds the shared error properties to a framework {@link ProblemDetail} that is passed
     * through unchanged, so every error response carries a stable code and the errors list.
     */
    private static void enrichFrameworkProblem(ProblemDetail problem, HttpStatusCode status) {
        var properties = problem.getProperties();
        if (properties == null || !properties.containsKey(CODE_PROPERTY)) {
            problem.setProperty(CODE_PROPERTY, frameworkCode(status));
        }
        properties = problem.getProperties();
        if (properties == null || !properties.containsKey(ERRORS_PROPERTY)) {
            problem.setProperty(ERRORS_PROPERTY, List.of());
        }
    }

    private static String frameworkCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "invalid_request";
            case 401 -> "unauthorized";
            case 403 -> "access_denied";
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            case 406 -> "not_acceptable";
            case 409 -> "conflict";
            case 413 -> "payload_too_large";
            case 415 -> "unsupported_media_type";
            case 429 -> "too_many_requests";
            case 500 -> "internal_error";
            case 502 -> "bad_gateway";
            case 503 -> "service_unavailable";
            case 504 -> "gateway_timeout";
            default -> status.is4xxClientError() ? "invalid_request"
                    : status.is5xxServerError() ? "internal_error" : "request_failed";
        };
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
