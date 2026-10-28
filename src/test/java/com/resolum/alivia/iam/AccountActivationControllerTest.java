package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.exceptions.VerificationTokenExpiredException;
import com.resolum.alivia.iam.domain.model.commands.ActivateAccountCommand;
import com.resolum.alivia.iam.domain.model.queries.GetAccountActivationQuery;
import com.resolum.alivia.iam.domain.model.valueobjects.AccountActivation;
import com.resolum.alivia.iam.domain.services.AccountActivationService;
import com.resolum.alivia.iam.interfaces.rest.controllers.AccountActivationController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountActivationControllerTest {
    private final AccountActivationService service = mock(AccountActivationService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(new AccountActivationController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void showsTheInvitationAndActivatesTheAccount() throws Exception {
        when(service.handle(any(GetAccountActivationQuery.class)))
                .thenReturn(new AccountActivation("ana@alivia.pe", "Ana Pérez", LocalDateTime.of(2026, 10, 29, 15, 0)));

        mvc.perform(get("/api/v1/auth/activation").param("token", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Pérez"));
        mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"password\":\"Clave#2026x\"}"))
                .andExpect(status().isNoContent());

        verify(service).handle(new ActivateAccountCommand("abc", "Clave#2026x"));
    }

    @Test
    void rejectsExpiredLinksAndIncompleteRequests() throws Exception {
        when(service.handle(any(GetAccountActivationQuery.class))).thenThrow(new VerificationTokenExpiredException());

        mvc.perform(get("/api/v1/auth/activation").param("token", "abc")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }
}
