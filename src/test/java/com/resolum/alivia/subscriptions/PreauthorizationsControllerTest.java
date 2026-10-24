package com.resolum.alivia.subscriptions;

import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentRejected;
import com.resolum.alivia.subscriptions.domain.exceptions.PaymentSetupUnauthorized;
import com.resolum.alivia.subscriptions.domain.model.commands.CreatePreauthorizationCommand;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.*;
import com.resolum.alivia.subscriptions.domain.services.PreauthorizationCommandService;
import com.resolum.alivia.subscriptions.domain.services.PreauthorizationQueryService;
import com.resolum.alivia.subscriptions.interfaces.rest.controllers.PreauthorizationsController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PreauthorizationsControllerTest {
    private final PreauthorizationCommandService commands = mock(PreauthorizationCommandService.class);
    private final PreauthorizationQueryService queries = mock(PreauthorizationQueryService.class);
    private final String path = "/api/v1/subscriptions/preauthorizations";

    @Test void createReturns201WithProviderStateAndAuthenticatedUser() throws Exception {
        var key = UUID.randomUUID();
        var deadline = Instant.parse("2026-10-30T12:00:00Z");
        when(commands.handle(any())).thenReturn(new PreauthorizationResult(new SubscriptionId(41L),
                new PaymentId(52L), PaymentStatus.PREAUTHORIZED, "pi_test", "secret", deadline));
        var mvc = MockMvcBuilders.standaloneSetup(new PreauthorizationsController(commands, queries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post(path).principal(() -> "7").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdId":"home-7","planId":1,"rooms":1,
                                 "actuators":[{"type":"LIGHT","quantity":1}],"paymentMethodId":"pm_test"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscriptionId").value(41))
                .andExpect(jsonPath("$.paymentId").value(52))
                .andExpect(jsonPath("$.status").value("PREAUTHORIZED"))
                .andExpect(jsonPath("$.clientSecret").value("secret"));
        verify(commands).handle(argThat(command -> command.authenticatedUserId().equals("7")
                && command.idempotencyKey().equals(key) && command.paymentMethodId().equals("pm_test")));
    }

    @Test void queryReturnsStateWithoutClientSecret() throws Exception {
        when(queries.get("7", 41L)).thenReturn(new PreauthorizationResult(new SubscriptionId(41L),
                new PaymentId(52L), PaymentStatus.CANCELED, "pi_test", null, null));
        var mvc = MockMvcBuilders.standaloneSetup(new PreauthorizationsController(commands, queries)).build();
        mvc.perform(get(path + "/41").principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.clientSecret").isEmpty());
        verify(queries).get("7", 41L);
    }

    @Test void rejectedCardAndMissingIdentityUseDocumentedHttpStatuses() throws Exception {
        when(commands.handle(any(CreatePreauthorizationCommand.class))).thenThrow(new PaymentRejected());
        when(queries.get(null, 41L)).thenThrow(new PaymentSetupUnauthorized());
        var mvc = MockMvcBuilders.standaloneSetup(new PreauthorizationsController(commands, queries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post(path).principal(() -> "7").header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdId":"home-7","planId":1,"rooms":1,
                                 "actuators":[{"type":"LIGHT","quantity":1}],"paymentMethodId":"pm_test"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("PAYMENT_REJECTED"));
        mvc.perform(get(path + "/41")).andExpect(status().isUnauthorized());
    }
}
