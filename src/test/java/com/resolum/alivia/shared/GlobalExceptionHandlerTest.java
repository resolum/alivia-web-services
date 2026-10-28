package com.resolum.alivia.shared;

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
import com.resolum.alivia.hcm.domain.exceptions.ContractNotActive;
import com.resolum.alivia.hcm.domain.exceptions.ContractNotFound;
import com.resolum.alivia.hcm.domain.exceptions.ContractOverlap;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotFound;
import com.resolum.alivia.hcm.domain.exceptions.EmployeeNotInBusiness;
import com.resolum.alivia.iam.domain.exceptions.AccountNotPendingVerificationException;
import com.resolum.alivia.iam.domain.exceptions.DniAlreadyRegisteredException;
import com.resolum.alivia.profiles.domain.exceptions.BusinessNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.BusinessProfileNotFoundException;
import com.resolum.alivia.profiles.domain.exceptions.DniAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.EmailAddressAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.LegalNameAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.PersonProfileNotFoundException;
import com.resolum.alivia.profiles.domain.exceptions.RucAlreadyExistsException;
import com.resolum.alivia.profiles.domain.exceptions.StaffProfileNotFoundException;
import com.resolum.alivia.shared.infrastructure.web.CorrelationIdFilter;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler.ApiFieldError;
import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationDraftNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidStripeWebhookSignature;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentRejected;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnavailable;
import com.resolum.alivia.subscriptions.domain.exceptions.PlanNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.PreauthorizationUnavailable;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookProcessingFailed;
import com.resolum.alivia.subscriptions.domain.exceptions.StripeWebhookUnavailable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();

    @Test
    void accessDeniedReturns403WithCode() throws Exception {
        mvc.perform(get("/probe/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void malformedBodyReturns400WithCode() throws Exception {
        mvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void invalidParameterTypeReturns400WithCode() throws Exception {
        mvc.perform(get("/probe/param").param("value", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void unexpectedFailureReturns500WithoutStackTrace() throws Exception {
        mvc.perform(get("/probe/failure").header(CorrelationIdFilter.HEADER, "corr-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.correlationId").value("corr-500"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void frameworkErrorKeepsItsStatusAndGetsCode() throws Exception {
        mvc.perform(post("/probe/forbidden"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("method_not_allowed"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void missingRequiredParameterReturns400WithCode() throws Exception {
        mvc.perform(get("/probe/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void paymentRejectedKeepsLegacyStatusProperty() throws Exception {
        mvc.perform(get("/probe/payment-rejected"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("payment_rejected"))
                .andExpect(jsonPath("$.status").value("PAYMENT_REJECTED"));
    }

    @ParameterizedTest
    @CsvSource({
            "/probe/email-exists, 409, email_already_registered",
            "/probe/dni-exists, 409, dni_already_registered",
            "/probe/ruc-exists, 409, ruc_already_exists",
            "/probe/business-name-exists, 409, business_name_already_exists",
            "/probe/legal-name-exists, 409, legal_name_already_exists",
            "/probe/person-profile-missing, 404, person_profile_not_found",
            "/probe/business-profile-missing, 404, business_profile_not_found",
            "/probe/staff-profile-missing, 404, staff_profile_not_found",
            "/probe/employee-missing, 404, employee_not_found",
            "/probe/invalid-argument, 400, invalid_argument",
            "/probe/contract-missing, 404, contract_not_found",
            "/probe/contract-overlap, 409, contract_overlap",
            "/probe/contract-not-active, 409, contract_not_active",
            "/probe/configuration-draft-missing, 404, configuration_draft_not_found",
            "/probe/employee-not-in-business, 403, employee_not_in_business",
            "/probe/preauthorization-unavailable, 503, preauthorization_unavailable",
            "/probe/payment-rejected, 422, payment_rejected",
            "/probe/stripe-processing-failed, 500, stripe_webhook_processing_failed",
            "/probe/stripe-signature-invalid, 400, invalid_stripe_webhook_signature",
            "/probe/stripe-unavailable, 503, stripe_webhook_unavailable",
            "/probe/plan-missing, 404, plan_not_found",
            "/probe/payment-setup-unavailable, 503, payment_setup_unavailable",
            "/probe/iam-dni-exists, 409, dni_already_registered",
            "/probe/account-not-pending, 400, verification_token_invalid"
    })
    void domainErrorsUseStableCodes(String path, int status, String code) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({
            "400, invalid_request",
            "401, unauthorized",
            "403, access_denied",
            "404, not_found",
            "405, method_not_allowed",
            "406, not_acceptable",
            "409, conflict",
            "413, payload_too_large",
            "415, unsupported_media_type",
            "429, too_many_requests",
            "500, internal_error",
            "502, bad_gateway",
            "503, service_unavailable",
            "504, gateway_timeout",
            "418, invalid_request",
            "599, internal_error",
            "302, request_failed"
    })
    void frameworkStatusesMapToStableCodes(int status, String code) {
        var response = new GlobalExceptionHandler().handleUnexpected(errorResponse(status, null));

        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", code)
                .containsEntry("errors", List.of());
    }

    @Test
    void frameworkProblemKeepsExistingCodeAndErrors() {
        var body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        body.setProperty("code", "custom_code");
        body.setProperty("errors", List.of(new ApiFieldError("email", "required")));

        var response = new GlobalExceptionHandler()
                .handleUnexpected(errorResponse(HttpStatus.BAD_REQUEST, body));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "custom_code")
                .containsEntry("errors", List.of(new ApiFieldError("email", "required")));
    }

    @Test
    void frameworkProblemWithOnlyCodeGetsEmptyErrors() {
        var body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        body.setProperty("code", "custom_code");

        var response = new GlobalExceptionHandler()
                .handleUnexpected(errorResponse(HttpStatus.BAD_REQUEST, body));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "custom_code")
                .containsEntry("errors", List.of());
    }

    @Test
    void frameworkProblemWithOnlyErrorsGetsCode() {
        var body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        body.setProperty("errors", List.of(new ApiFieldError("email", "required")));

        var response = new GlobalExceptionHandler()
                .handleUnexpected(errorResponse(HttpStatus.BAD_REQUEST, body));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "invalid_request")
                .containsEntry("errors", List.of(new ApiFieldError("email", "required")));
    }

    @Test
    void iamDniConflictPointsToTheDniField() throws Exception {
        mvc.perform(get("/probe/iam-dni-exists"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("dni_already_registered"))
                .andExpect(jsonPath("$.errors[0].field").value("dni"))
                .andExpect(jsonPath("$.errors[0].code").value("dni_already_registered"));
    }

    @Test
    void beanValidationErrorsMapToStableFieldCodes() throws Exception {
        String json = """
                {
                    "termsAccepted": false,
                    "nickname": "abcdef",
                    "code": "abcd",
                    "name": " ",
                    "tags": "",
                    "email": "not-an-email"
                }
                """;

        mvc.perform(post("/probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors[?(@.field=='termsAccepted')].code",
                        contains("terms_not_accepted")))
                .andExpect(jsonPath("$.errors[?(@.field=='nickname')].code", contains("too_long")))
                .andExpect(jsonPath("$.errors[?(@.field=='code')].code", contains("too_long")))
                .andExpect(jsonPath("$.errors[?(@.field=='name')].code", contains("required")))
                .andExpect(jsonPath("$.errors[?(@.field=='phone')].code", contains("required")))
                .andExpect(jsonPath("$.errors[?(@.field=='tags')].code", contains("required")))
                .andExpect(jsonPath("$.errors[?(@.field=='email')].code", contains("invalid")));
    }

    @Test
    void unsupportedMediaTypeReturns415WithCode() throws Exception {
        mvc.perform(post("/probe/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_media_type"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    record ProbeBody(String name) {
    }

    record ValidatedBody(
            @AssertTrue Boolean termsAccepted,
            @Size(max = 5) String nickname,
            @Length(max = 3) String code,
            @NotBlank String name,
            @NotNull String phone,
            @NotEmpty String tags,
            @Email String email
    ) {
    }

    private static StubErrorResponse errorResponse(int status, ProblemDetail body) {
        return new StubErrorResponse(HttpStatusCode.valueOf(status), body);
    }

    private static StubErrorResponse errorResponse(HttpStatusCode status, ProblemDetail body) {
        return new StubErrorResponse(status, body);
    }

    /**
     * Minimal {@link ErrorResponse} that is also a {@link RuntimeException}, so it can be
     * passed to the {@code @ExceptionHandler(Exception.class)} method under test.
     */
    private static final class StubErrorResponse extends RuntimeException implements ErrorResponse {

        private final HttpStatusCode status;
        private final ProblemDetail body;

        private StubErrorResponse(HttpStatusCode status, ProblemDetail body) {
            super("stub error response");
            this.status = status;
            this.body = body;
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return status;
        }

        @Override
        public ProblemDetail getBody() {
            return body;
        }
    }

    @Test
    void businessExceptionsAreHandledWithAppropriateStatusAndCode() {
        var handler = new GlobalExceptionHandler();

        var bnf = handler.handleBusinessNotFound(new BusinessNotFound(1L));
        assertThat(bnf.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(bnf.getProperties()).containsEntry("code", "business_not_found");

        var anf = handler.handleAdministratorNotFound(new AdministratorNotFound(2L));
        assertThat(anf.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(anf.getProperties()).containsEntry("code", "administrator_not_found");

        var bae = handler.handleBusinessAlreadyExistsForAdministrator(new BusinessAlreadyExistsForAdministrator(1L));
        assertThat(bae.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(bae.getProperties()).containsEntry("code", "business_already_exists_for_administrator");

        var dae = handler.handleDniAlreadyExists(new DniAlreadyExists("12345678"));
        assertThat(dae.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(dae.getProperties()).containsEntry("code", "dni_already_registered");

        var eae = handler.handleEmailAddressAlreadyExists(new EmailAddressAlreadyExists("test@test.com"));
        assertThat(eae.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(eae.getProperties()).containsEntry("code", "email_already_registered");

        var bne = handler.handleBusinessNameAlreadyExists(new BusinessNameAlreadyExists("Clinica"));
        assertThat(bne.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(bne.getProperties()).containsEntry("code", "business_name_already_exists");

        var lne = handler.handleLegalNameAlreadyExists(new LegalNameAlreadyExists("Clinica SAC"));
        assertThat(lne.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(lne.getProperties()).containsEntry("code", "legal_name_already_exists");

        var rae = handler.handleRucAlreadyExists(new RucAlreadyExists(20601234567L));
        assertThat(rae.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(rae.getProperties()).containsEntry("code", "ruc_already_exists");

        var bpe = handler.handleBackupPersonalEmailAlreadyExists(new BackupPersonalEmailAlreadyExists("backup@test.com"));
        assertThat(bpe.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(bpe.getProperties()).containsEntry("code", "backup_personal_email_already_exists");

        var uae = handler.handleUnauthorizedAdministrator(new UnauthorizedAdministratorException("Unauthorized"));
        assertThat(uae.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(uae.getProperties()).containsEntry("code", "unauthorized_administrator");
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/forbidden")
        void forbidden() {
            throw new AccessDeniedException("denied");
        }

        @PostMapping("/probe/body")
        void body(@RequestBody ProbeBody body) {
        }

        @GetMapping("/probe/param")
        void param(@RequestParam Integer value) {
        }

        @GetMapping("/probe/failure")
        void failure() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/probe/email-exists")
        void emailExists() {
            throw new EmailAddressAlreadyExistsException("corp@alivia.com");
        }

        @GetMapping("/probe/dni-exists")
        void dniExists() {
            throw new DniAlreadyExistsException("12345678");
        }

        @GetMapping("/probe/iam-dni-exists")
        void iamDniExists() {
            throw new DniAlreadyRegisteredException();
        }

        @GetMapping("/probe/account-not-pending")
        void accountNotPending() {
            throw new AccountNotPendingVerificationException();
        }

        @PostMapping("/probe/validated")
        void validated(@Valid @RequestBody ValidatedBody body) {
        }

        @GetMapping("/probe/ruc-exists")
        void rucExists() {
            throw new RucAlreadyExistsException("20123456789");
        }

        @GetMapping("/probe/business-name-exists")
        void businessNameExists() {
            throw new BusinessNameAlreadyExistsException("Alivia Lab");
        }

        @GetMapping("/probe/legal-name-exists")
        void legalNameExists() {
            throw new LegalNameAlreadyExistsException("Alivia Lab SAC");
        }

        @GetMapping("/probe/person-profile-missing")
        void personProfileMissing() {
            throw new PersonProfileNotFoundException(99L);
        }

        @GetMapping("/probe/business-profile-missing")
        void businessProfileMissing() {
            throw new BusinessProfileNotFoundException(99L);
        }

        @GetMapping("/probe/staff-profile-missing")
        void staffProfileMissing() {
            throw new StaffProfileNotFoundException(99L);
        }

        @GetMapping("/probe/employee-missing")
        void employeeMissing() {
            throw new EmployeeNotFound(99L);
        }

        @GetMapping("/probe/invalid-argument")
        void invalidArgument() {
            throw new IllegalArgumentException("The argument is invalid.");
        }

        @GetMapping("/probe/contract-missing")
        void contractMissing() {
            throw new ContractNotFound(99L);
        }

        @GetMapping("/probe/contract-overlap")
        void contractOverlap() {
            throw new ContractOverlap(99L);
        }

        @GetMapping("/probe/contract-not-active")
        void contractNotActive() {
            throw new ContractNotActive();
        }

        @GetMapping("/probe/configuration-draft-missing")
        void configurationDraftMissing() {
            throw new ConfigurationDraftNotFound("draft-99");
        }

        @GetMapping("/probe/employee-not-in-business")
        void employeeNotInBusiness() {
            throw new EmployeeNotInBusiness(99L, 1L);
        }

        @GetMapping("/probe/preauthorization-unavailable")
        void preauthorizationUnavailable() {
            throw new PreauthorizationUnavailable("Preauthorization is unavailable.");
        }

        @GetMapping("/probe/payment-rejected")
        void paymentRejected() {
            throw new PaymentRejected();
        }

        @GetMapping("/probe/stripe-processing-failed")
        void stripeProcessingFailed() {
            throw new StripeWebhookProcessingFailed(new IllegalStateException("boom"));
        }

        @GetMapping("/probe/stripe-signature-invalid")
        void stripeSignatureInvalid() {
            throw new InvalidStripeWebhookSignature();
        }

        @GetMapping("/probe/stripe-unavailable")
        void stripeUnavailable() {
            throw new StripeWebhookUnavailable();
        }

        @GetMapping("/probe/plan-missing")
        void planMissing() {
            throw new PlanNotFound("UNKNOWN");
        }

        @GetMapping("/probe/payment-setup-unavailable")
        void paymentSetupUnavailable() {
            throw new PaymentSetupUnavailable("Payment setup is unavailable.");
        }
    }
}
