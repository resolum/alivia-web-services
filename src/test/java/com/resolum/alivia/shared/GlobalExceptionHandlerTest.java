package com.resolum.alivia.shared;

import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
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
        mvc.perform(get("/probe/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void frameworkErrorKeepsItsStatus() throws Exception {
        mvc.perform(post("/probe/forbidden"))
                .andExpect(status().isMethodNotAllowed());
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
    }
}
