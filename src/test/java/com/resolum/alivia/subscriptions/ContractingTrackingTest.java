package com.resolum.alivia.subscriptions;

import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade;
import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade.ContractingVisit;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.subscriptions.application.internal.commandservices.ContractingCommandServiceImpl;
import com.resolum.alivia.subscriptions.application.internal.outboundservices.acl.ExternalOperationsService;
import com.resolum.alivia.subscriptions.application.internal.queryservices.ContractingTrackingQueryServiceImpl;
import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationDraftNotFound;
import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationNotReadyToConfirm;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidActuatorSelection;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.domain.model.commands.ConfirmConfigurationCommand;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfiguredRoom;
import com.resolum.alivia.subscriptions.domain.model.queries.GetContractingTrackingQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.QuotePlanQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ContractingStage;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanQuote;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.interfaces.rest.controllers.ContractingTrackingController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContractingTrackingTest {
    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");
    private static final LocalDate VISIT_DAY = LocalDate.of(2026, 11, 3);

    private ConfigurationDraftRepository drafts;
    private OperationsContextFacade operationsFacade;
    private ContractingTrackingQueryServiceImpl queries;
    private ContractingCommandServiceImpl commands;
    private ConfigurationDraft draft;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        drafts = mock(ConfigurationDraftRepository.class);
        operationsFacade = mock(OperationsContextFacade.class);
        var plans = mock(PlanQueryService.class);
        var plan = mock(Plan.class);
        when(plan.getName()).thenReturn("Plan Personalizado");
        when(plans.handle(any(GetPlanByCodeQuery.class))).thenReturn(plan);
        when(plans.handle(any(QuotePlanQuery.class))).thenReturn(new PlanQuote(PlanCode.CUSTOM, "PEN", 3, Map.of(),
                BigDecimal.valueOf(129), BigDecimal.valueOf(30), BigDecimal.valueOf(20), BigDecimal.valueOf(179)));
        queries = new ContractingTrackingQueryServiceImpl(drafts, plans, new ExternalOperationsService(operationsFacade));
        commands = new ContractingCommandServiceImpl(drafts, queries, new ExternalOperationsService(operationsFacade),
                Clock.fixed(NOW, ZoneOffset.UTC));
        draft = draft();
        when(drafts.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(drafts.findById("missing")).thenReturn(Optional.empty());
        mvc = MockMvcBuilders.standaloneSetup(new ContractingTrackingController(queries, commands))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void staysInEvaluationUntilTheTechnicianRecordsTheResult() throws Exception {
        when(operationsFacade.fetchContractingVisitsOfDraft(draft.getId())).thenReturn(List.of())
                .thenReturn(List.of(evaluation("SCHEDULED", null, null)));

        assertEquals(ContractingStage.EVALUATION, queries.handle(new GetContractingTrackingQuery(draft.getId())).stage());
        mvc.perform(get("/api/v1/configuration-drafts/{id}/tracking", draft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("EVALUATION"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("SOL-")))
                .andExpect(jsonPath("$.planName").value("Plan Personalizado"))
                .andExpect(jsonPath("$.monthlyAmount").value(179))
                .andExpect(jsonPath("$.rooms").value(3))
                .andExpect(jsonPath("$.evaluation.startTime").value("10:00"))
                .andExpect(jsonPath("$.evaluation.technicianName").value("Carla Mendoza"))
                .andExpect(jsonPath("$.evaluation.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.installation").doesNotExist());
        mvc.perform(post("/api/v1/configuration-drafts/{id}/confirmation", draft.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("configuration_not_ready_to_confirm"));
        assertFalse(draft.isConfirmed());
    }

    @Test
    void theFamilyConfirmsAViableHomeAndMovesToInstallation() throws Exception {
        when(operationsFacade.fetchContractingVisitsOfDraft(draft.getId()))
                .thenReturn(List.of(evaluation("COMPLETED", "VIABLE_WITH_ADAPTATION", "1 sensor adicional en puerta posterior")));

        mvc.perform(get("/api/v1/configuration-drafts/{id}/tracking", draft.getId()))
                .andExpect(jsonPath("$.stage").value("CONFIRMATION"))
                .andExpect(jsonPath("$.evaluation.result").value("VIABLE_WITH_ADAPTATION"))
                .andExpect(jsonPath("$.evaluation.notes").value("1 sensor adicional en puerta posterior"));
        mvc.perform(post("/api/v1/configuration-drafts/{id}/confirmation", draft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("INSTALLATION"))
                .andExpect(jsonPath("$.confirmedAt").exists());
        assertEquals(NOW, draft.getConfirmedAt());
        verify(drafts).save(draft);
        // Confirming hands the installation to the technician who evaluated the home.
        verify(operationsFacade).openInstallationOfConfirmedRequest(draft.getId());

        var again = commands.handle(new ConfirmConfigurationCommand(draft.getId()));
        assertEquals(ContractingStage.INSTALLATION, again.stage());
        verify(drafts, times(1)).save(draft);
        // A repeated confirmation only makes sure the installation exists; Operations does not duplicate it.
        verify(operationsFacade, times(2)).openInstallationOfConfirmedRequest(draft.getId());
    }

    @Test
    void anApprovedInstallationLeavesOnlyTheActivationAndANotViableHomeClosesTheRequest() {
        var installation = new ContractingVisit("INSTALLATION", "COMPLETED", VISIT_DAY.plusDays(3), LocalTime.of(8, 0),
                LocalTime.of(12, 0), "Carla Mendoza", null, "APPROVED", null);
        when(operationsFacade.fetchContractingVisitsOfDraft(draft.getId()))
                .thenReturn(List.of(evaluation("COMPLETED", "VIABLE", null), installation))
                .thenReturn(List.of(evaluation("COMPLETED", "NOT_VIABLE", "Sin red eléctrica estable")));
        draft.confirm(NOW);

        var active = queries.handle(new GetContractingTrackingQuery(draft.getId()));
        assertEquals(ContractingStage.ACTIVATION, active.stage());
        assertEquals("APPROVED", active.installation().result());
        assertTrue(active.installation().isInstallation());
        assertTrue(active.evaluation().isEvaluation());

        var other = draft();
        when(drafts.findById(other.getId())).thenReturn(Optional.of(other));
        when(operationsFacade.fetchContractingVisitsOfDraft(other.getId()))
                .thenReturn(List.of(evaluation("COMPLETED", "NOT_VIABLE", "Sin red eléctrica estable")));
        assertEquals(ContractingStage.NOT_VIABLE, queries.handle(new GetContractingTrackingQuery(other.getId())).stage());
        assertThrows(ConfigurationNotReadyToConfirm.class, () -> commands.handle(new ConfirmConfigurationCommand(other.getId())));
        verify(operationsFacade, never()).openInstallationOfConfirmedRequest(other.getId());
    }

    @Test
    void answersNotFoundForUnknownDraftsAndNoAmountWhenTheQuoteFails() throws Exception {
        mvc.perform(get("/api/v1/configuration-drafts/missing/tracking")).andExpect(status().isNotFound());
        assertThrows(ConfigurationDraftNotFound.class, () -> commands.handle(new ConfirmConfigurationCommand("missing")));

        var plans = mock(PlanQueryService.class);
        when(plans.handle(any(GetPlanByCodeQuery.class))).thenReturn(mock(Plan.class));
        when(plans.handle(any(QuotePlanQuery.class))).thenThrow(new InvalidActuatorSelection("Fuera de los límites."));
        when(operationsFacade.fetchContractingVisitsOfDraft(draft.getId())).thenReturn(List.of());
        var tracking = new ContractingTrackingQueryServiceImpl(drafts, plans, new ExternalOperationsService(operationsFacade))
                .handle(new GetContractingTrackingQuery(draft.getId()));
        assertNull(tracking.monthlyAmount());
        assertEquals("PEN", tracking.currency());
        assertThrows(IllegalArgumentException.class, () -> draft.confirm(null));
    }

    private static ContractingVisit evaluation(String status, String result, String notes) {
        return new ContractingVisit("EVALUATION", status, VISIT_DAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                "Carla Mendoza", "Av. Del Parque Norte 742, Dpto. 401", result, notes);
    }

    private static ConfigurationDraft draft() {
        var draft = new ConfigurationDraft(PlanCode.CUSTOM, "MONTHLY");
        var rooms = new ArrayList<ConfiguredRoom>();
        for (int index = 0; index < 3; index++) rooms.add(new ConfiguredRoom("room-" + index, "Dormitorio " + index, 1, 0, 0, 0, 1));
        ReflectionTestUtils.setField(draft, "rooms", rooms);
        ReflectionTestUtils.setField(draft, "paymentStatus", PaymentStatus.PREAUTHORIZED);
        return draft;
    }
}
