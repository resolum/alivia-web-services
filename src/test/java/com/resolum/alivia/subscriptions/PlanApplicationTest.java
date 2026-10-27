package com.resolum.alivia.subscriptions;

import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.subscriptions.application.internal.commandservices.PlanCommandServiceImpl;
import com.resolum.alivia.subscriptions.application.internal.eventhandlers.PlanCatalogInitializer;
import com.resolum.alivia.subscriptions.application.internal.queryservices.PlanQueryServiceImpl;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidActuatorSelection;
import com.resolum.alivia.subscriptions.domain.exceptions.PlanNotFound;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.domain.model.commands.SeedPlansCommand;
import com.resolum.alivia.subscriptions.domain.model.factories.PlanCatalogDefaults;
import com.resolum.alivia.subscriptions.domain.model.queries.GetAllPlansQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.QuotePlanQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorQuantities;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanStatus;
import com.resolum.alivia.subscriptions.domain.services.PlanCommandService;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.PlanRepository;
import com.resolum.alivia.subscriptions.interfaces.rest.controllers.PlansController;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlanApplicationTest {
    @Test
    void seedsOnlyMissingCatalogEntries() {
        PlanRepository repository = mock(PlanRepository.class);
        when(repository.existsByCode(PlanCode.MINIMUM)).thenReturn(false);
        when(repository.existsByCode(PlanCode.CUSTOM)).thenReturn(true);

        new PlanCommandServiceImpl(repository).handle(new SeedPlansCommand());

        verify(repository).save(argThat(plan -> plan.getCode() == PlanCode.MINIMUM));
        verify(repository, times(1)).save(any(Plan.class));
        verify(repository, never()).save(argThat(plan -> plan.getCode() == PlanCode.CUSTOM));
    }

    @Test
    void initializerDelegatesCatalogSeeding() {
        PlanCommandService commandService = mock(PlanCommandService.class);
        new PlanCatalogInitializer(commandService).initialize();
        verify(commandService).handle(any(SeedPlansCommand.class));
    }

    @Test
    void queryServiceListsAndFindsOnlyActivePlans() {
        PlanRepository repository = mock(PlanRepository.class);
        Plan base = PlanCatalogDefaults.base();
        Plan custom = PlanCatalogDefaults.custom();
        when(repository.findByStatusOrderByIdAsc(PlanStatus.ACTIVE)).thenReturn(List.of(base, custom));
        when(repository.findByCodeAndStatus(PlanCode.CUSTOM, PlanStatus.ACTIVE))
                .thenReturn(Optional.of(custom));
        PlanQueryServiceImpl service = new PlanQueryServiceImpl(repository);

        assertEquals(List.of(base, custom), service.handle(new GetAllPlansQuery(PlanStatus.ACTIVE)));
        assertSame(custom, service.handle(new GetPlanByCodeQuery(PlanCode.CUSTOM)));
        verify(repository).findByStatusOrderByIdAsc(PlanStatus.ACTIVE);
        verify(repository).findByCodeAndStatus(PlanCode.CUSTOM, PlanStatus.ACTIVE);
    }

    @Test
    void queryServiceQuotesDefaultRoomsAndRejectsMissingPlans() {
        PlanRepository repository = mock(PlanRepository.class);
        when(repository.findByCodeAndStatus(PlanCode.CUSTOM, PlanStatus.ACTIVE))
                .thenReturn(Optional.of(PlanCatalogDefaults.custom()));
        PlanQueryServiceImpl service = new PlanQueryServiceImpl(repository);

        var quote = service.handle(new QuotePlanQuery(PlanCode.CUSTOM, null,
                new ActuatorQuantities(1, 0, 0, 0)));
        assertEquals(2, quote.rooms());
        assertEquals(new BigDecimal("164.00"), quote.totalMonthlyAmount());
        assertThrows(InvalidActuatorSelection.class,
                () -> service.handle(new QuotePlanQuery(PlanCode.CUSTOM, 1,
                        new ActuatorQuantities(1, 0, 0, 0))));
        assertThrows(PlanNotFound.class,
                () -> service.handle(new GetPlanByCodeQuery(PlanCode.MINIMUM)));
        assertThrows(PlanNotFound.class,
                () -> service.handle(new QuotePlanQuery(PlanCode.MINIMUM, 1,
                        new ActuatorQuantities(1, 0, 0, 0))));
    }

    @Test
    void controllerExposesCatalogDetailsAndCalculatedPrices() {
        PlanQueryService queryService = mock(PlanQueryService.class);
        when(queryService.handle(any(GetAllPlansQuery.class)))
                .thenReturn(List.of(PlanCatalogDefaults.base(), PlanCatalogDefaults.custom()));
        when(queryService.handle(any(GetPlanByCodeQuery.class))).thenReturn(PlanCatalogDefaults.custom());
        when(queryService.handle(any(QuotePlanQuery.class))).thenReturn(
                PlanCatalogDefaults.custom().quoteMonthly(3,
                        new ActuatorQuantities(1, 1, 0, 0).toSelection()));
        PlansController controller = new PlansController(queryService);

        assertEquals(2, controller.getPlans(PlanStatus.ACTIVE).size());
        assertEquals(PlanCode.CUSTOM, controller.getPlan("custom").code());
        var price = controller.getPricing("CUSTOM", 3, 1, 1, 0, 0);
        assertEquals(new BigDecimal("224.00"), price.totalMonthlyAmount());
        assertEquals("MONTHLY", price.billingPeriod());
        assertThrows(PlanNotFound.class, () -> controller.getPlan("unknown"));
    }

    @Test
    void exceptionHandlerUsesAppropriateHttpStatuses() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        assertEquals(400, handler.handleInvalidArgument(new IllegalArgumentException("inválido")).getStatus());
        assertEquals(404, handler.handlePlanNotFound(new PlanNotFound("UNKNOWN")).getStatus());
    }
}
