package com.resolum.alivia.subscriptions;

import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.subscriptions.application.internal.queryservices.HiringRequestQueryServiceImpl;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidActuatorSelection;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfiguredRoom;
import com.resolum.alivia.subscriptions.domain.model.queries.GetHiringRequestsQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.QuotePlanQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringRequest;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringStage;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanQuote;
import com.resolum.alivia.subscriptions.domain.services.HiringRequestQueryService;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.interfaces.rest.controllers.HiringRequestsController;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HiringRequestsTest {

    @Test
    void listsConfiguredDraftsWithTheirHeldAmount() {
        var drafts = mock(ConfigurationDraftRepository.class);
        var plans = mock(PlanQueryService.class);
        var plan = mock(Plan.class);
        when(plan.getName()).thenReturn("Plan Base");
        when(plans.handle(any(GetPlanByCodeQuery.class))).thenReturn(plan);
        var preauthorized = draft(PlanCode.MINIMUM, PaymentStatus.PREAUTHORIZED, 1);
        var invalid = draft(PlanCode.MINIMUM, null, 2);
        var empty = draft(PlanCode.MINIMUM, null, 0);
        when(drafts.findAll(any(Sort.class))).thenReturn(List.of(preauthorized, invalid, empty));
        when(plans.handle(any(QuotePlanQuery.class)))
                .thenReturn(new PlanQuote(PlanCode.MINIMUM, "PEN", 1, Map.of(), BigDecimal.valueOf(99),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(99)))
                .thenThrow(new InvalidActuatorSelection("El plan permite entre 1 y 1 actuador(es)."));
        var service = new HiringRequestQueryServiceImpl(drafts, plans);

        var requests = service.handle(new GetHiringRequestsQuery(null));

        assertEquals(2, requests.size());
        var first = requests.getFirst();
        assertEquals(HiringStage.REQUEST_AND_PAYMENT, first.stage());
        assertEquals(BigDecimal.valueOf(99), first.heldAmount());
        assertEquals(2, first.devices());
        assertNull(requests.get(1).heldAmount());
        assertTrue(service.handle(new GetHiringRequestsQuery(HiringStage.INSTALLATION)).isEmpty());
    }

    @Test
    void exposesTheRequestsWithAReadableCode() throws Exception {
        var service = mock(HiringRequestQueryService.class);
        when(service.handle(new GetHiringRequestsQuery(HiringStage.REQUEST_AND_PAYMENT))).thenReturn(List.of(
                new HiringRequest("5cf5d80a-4f88-44ae-9ccb-642c68c065ab", PlanCode.CUSTOM, "Plan Personalizado",
                        "MONTHLY", 2, 5, HiringStage.REQUEST_AND_PAYMENT, PaymentStatus.PREAUTHORIZED,
                        BigDecimal.valueOf(204), "PEN", Instant.parse("2026-10-29T10:00:00Z"), null,
                        Instant.parse("2026-10-29T10:00:00Z"))));
        var mvc = MockMvcBuilders.standaloneSetup(new HiringRequestsController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/hiring-requests").param("stage", "REQUEST_AND_PAYMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SOL-68C065AB"))
                .andExpect(jsonPath("$[0].paymentStatus").value("PREAUTHORIZED"))
                .andExpect(jsonPath("$[0].heldAmount").value(204));
    }

    private static ConfigurationDraft draft(PlanCode code, PaymentStatus status, int rooms) {
        var draft = new ConfigurationDraft(code, "MONTHLY");
        var configured = new java.util.ArrayList<ConfiguredRoom>();
        for (int index = 0; index < rooms; index++) {
            configured.add(new ConfiguredRoom("room-" + index, "Dormitorio " + index, 1, 0, 0, 0, 1));
        }
        ReflectionTestUtils.setField(draft, "rooms", configured);
        ReflectionTestUtils.setField(draft, "paymentStatus", status);
        return draft;
    }
}
