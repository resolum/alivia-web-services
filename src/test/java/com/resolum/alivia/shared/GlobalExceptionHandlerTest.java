package com.resolum.alivia.shared;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    void frameworkErrorKeepsItsStatus() throws Exception {
        mvc.perform(post("/probe/forbidden"))
                .andExpect(status().isMethodNotAllowed());
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
            "/probe/staff-profile-missing, 404, staff_profile_not_found"
    })
    void profileErrorsUseStableCodes(String path, int status, String code) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    record ProbeBody(String name) {
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
    }
}
