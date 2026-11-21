package com.resolum.alivia.subscriptions.interfaces.rest.controllers;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.domain.model.factories.PlanCatalogDefaults;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.commands.RequestPlanChangeCommand;
import com.resolum.alivia.subscriptions.domain.model.commands.UpdateSubscriptionStatusCommand;
import com.resolum.alivia.subscriptions.domain.model.queries.GetActiveSubscriptionByUserIdQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.*;
import com.resolum.alivia.subscriptions.domain.services.SubscriptionCommandService;
import com.resolum.alivia.subscriptions.domain.services.SubscriptionQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.ChangeSubscriptionStatusResource;
import com.resolum.alivia.subscriptions.interfaces.rest.resources.RequestPlanChangeResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.profiles.interfaces.acl.ProfilesContextFacade;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfiguredRoom;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubscriptionsControllerTest {

    private SubscriptionQueryService queryService;
    private SubscriptionCommandService commandService;
    private PaymentRepository paymentRepository;
    private SubscriptionsController controller;

    @BeforeEach
    void setUp() {
        queryService = mock(SubscriptionQueryService.class);
        commandService = mock(SubscriptionCommandService.class);
        paymentRepository = mock(PaymentRepository.class);
        controller = new SubscriptionsController(queryService, commandService, paymentRepository);
    }

    private Subscription createMockSubscription() {
        Plan plan = PlanCatalogDefaults.base();
        ActuatorSelection selection = new ActuatorSelection(List.of(new ActuatorQuantity(ActuatorType.DOOR, 1)));
        return new Subscription(new UserId(1L), new HouseholdId("hh-1"), plan, selection,
                new Money(new BigDecimal("99.00"), "PEN"), "cus_123", "idemp-1");
    }

    @Test
    void getCurrentReturnsSubscription() {
        Subscription subscription = createMockSubscription();
        when(queryService.handle(any(GetActiveSubscriptionByUserIdQuery.class))).thenReturn(Optional.of(subscription));
        when(paymentRepository.findFirstBySubscription_IdOrderByIdDesc(any())).thenReturn(Optional.empty());

        Principal principal = () -> "1";
        var response = controller.getCurrent(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MINIMUM", response.getBody().planCode());
    }

    @Test
    void updateStatusCallsCommandService() {
        Subscription subscription = createMockSubscription();
        when(commandService.handle(any(UpdateSubscriptionStatusCommand.class))).thenReturn(subscription);
        when(paymentRepository.findFirstBySubscription_IdOrderByIdDesc(any())).thenReturn(Optional.empty());

        Principal principal = () -> "1";
        var response = controller.updateStatus(1L, new ChangeSubscriptionStatusResource("cancelled"), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(commandService).handle(any(UpdateSubscriptionStatusCommand.class));
    }

    @Test
    void requestPlanChangeCallsCommandService() {
        Subscription subscription = createMockSubscription();
        when(commandService.handle(any(RequestPlanChangeCommand.class))).thenReturn(subscription);
        when(paymentRepository.findFirstBySubscription_IdOrderByIdDesc(any())).thenReturn(Optional.empty());

        Principal principal = () -> "1";
        var response = controller.requestPlanChange(1L, new RequestPlanChangeResource("CUSTOM"), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(commandService).handle(any(RequestPlanChangeCommand.class));
    }

    @Test
    void getCurrentEnrichesHolderNameAndRoomsFromDraft() {
        var iam = mock(IamContextFacade.class);
        var profiles = mock(ProfilesContextFacade.class);
        var drafts = mock(ConfigurationDraftRepository.class);
        var enrichedController = new SubscriptionsController(
                queryService, commandService, paymentRepository, iam, profiles, drafts);

        Subscription subscription = createMockSubscription();
        ReflectionTestUtils.setField(subscription, "draftId", "draft-1");
        ConfigurationDraft draft = mock(ConfigurationDraft.class);
        when(draft.getRooms()).thenReturn(List.of(
                mock(ConfiguredRoom.class), mock(ConfiguredRoom.class), mock(ConfiguredRoom.class)));

        when(queryService.handle(any(GetActiveSubscriptionByUserIdQuery.class))).thenReturn(Optional.of(subscription));
        when(paymentRepository.findFirstBySubscription_IdOrderByIdDesc(any())).thenReturn(Optional.empty());
        when(iam.fetchPersonProfileIdsByUserIds(Set.of(1L))).thenReturn(Map.of(1L, 50L));
        when(profiles.fetchPersonProfileFullNameById(50L)).thenReturn(Optional.of("Dayro Rios"));
        when(drafts.findById("draft-1")).thenReturn(Optional.of(draft));

        var response = enrichedController.getCurrent(() -> "1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Dayro Rios", response.getBody().paymentMethod().holderName());
        assertEquals(3, response.getBody().rooms());

        when(queryService.handle(any(GetActiveSubscriptionByUserIdQuery.class))).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, enrichedController.getCurrent(() -> "1").getStatusCode());
    }
}
