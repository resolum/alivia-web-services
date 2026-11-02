package com.resolum.alivia.subscriptions;

import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade;
import com.resolum.alivia.operations.interfaces.acl.OperationsContextFacade.ScheduledVisit;
import com.resolum.alivia.subscriptions.application.internal.outboundservices.acl.ExternalOperationsService;
import com.resolum.alivia.subscriptions.application.internal.queryservices.HiringRequestQueryServiceImpl;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfiguredRoom;
import com.resolum.alivia.subscriptions.domain.model.queries.GetHiringRequestsQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.QuotePlanQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HiringStage;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanQuote;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HiringStageFromOperationsTest {

    @Test
    void movesRequestsToTheStageOfTheirScheduledTechnicalOrder() {
        var evaluated = draft();
        var installing = draft();
        var waiting = draft();
        var drafts = mock(ConfigurationDraftRepository.class);
        when(drafts.findAll(any(Sort.class))).thenReturn(List.of(evaluated, installing, waiting));
        var plans = mock(PlanQueryService.class);
        var plan = mock(Plan.class);
        when(plan.getName()).thenReturn("Plan Base");
        when(plans.handle(any(GetPlanByCodeQuery.class))).thenReturn(plan);
        when(plans.handle(any(QuotePlanQuery.class))).thenReturn(new PlanQuote(PlanCode.MINIMUM, "PEN", 1, Map.of(),
                BigDecimal.valueOf(99), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(99)));
        var facade = mock(OperationsContextFacade.class);
        when(facade.fetchScheduledVisitsOfDrafts(any())).thenReturn(Map.of(
                evaluated.getId(), new ScheduledVisit("EVALUATION", LocalDate.of(2026, 11, 5)),
                installing.getId(), new ScheduledVisit("INSTALLATION", LocalDate.of(2026, 11, 9))));
        var service = new HiringRequestQueryServiceImpl(drafts, plans, new ExternalOperationsService(facade));

        var stages = service.handle(new GetHiringRequestsQuery(null)).stream()
                .collect(java.util.stream.Collectors.toMap(request -> request.draftId(), request -> request.stage()));

        assertEquals(HiringStage.TECHNICAL_EVALUATION, stages.get(evaluated.getId()));
        assertEquals(HiringStage.INSTALLATION, stages.get(installing.getId()));
        assertEquals(HiringStage.REQUEST_AND_PAYMENT, stages.get(waiting.getId()));
        assertEquals(1, service.handle(new GetHiringRequestsQuery(HiringStage.INSTALLATION)).size());
    }

    private static ConfigurationDraft draft() {
        var draft = new ConfigurationDraft(PlanCode.MINIMUM, "MONTHLY");
        ReflectionTestUtils.setField(draft, "rooms",
                new java.util.ArrayList<>(List.of(new ConfiguredRoom("room", "Dormitorio", 1, 0, 0, 0, 1))));
        ReflectionTestUtils.setField(draft, "paymentStatus", PaymentStatus.PREAUTHORIZED);
        return draft;
    }
}
