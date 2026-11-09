package com.resolum.alivia.subscriptions.application.internal.commandservices;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.domain.model.factories.PlanCatalogDefaults;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.commands.RequestPlanChangeCommand;
import com.resolum.alivia.subscriptions.domain.model.commands.UpdateSubscriptionStatusCommand;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.*;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubscriptionCommandServiceImplTest {

    private SubscriptionRepository subscriptionRepository;
    private SubscriptionCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        service = new SubscriptionCommandServiceImpl(subscriptionRepository);
    }

    private Subscription createMockSubscription() {
        Plan plan = PlanCatalogDefaults.base();
        ActuatorSelection selection = new ActuatorSelection(List.of(new ActuatorQuantity(ActuatorType.DOOR, 1)));
        return new Subscription(new UserId(1L), new HouseholdId("hh-1"), plan, selection,
                new Money(new BigDecimal("99.00"), "PEN"), "cus_123", "idemp-1");
    }

    @Test
    void updatesSubscriptionStatus() {
        Subscription subscription = createMockSubscription();
        when(subscriptionRepository.findById(10L)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.handle(new UpdateSubscriptionStatusCommand(10L, 1L, SubscriptionStatus.CANCELED));

        assertEquals(SubscriptionStatus.CANCELED, result.getStatus());
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void requestsPlanChange() {
        Subscription subscription = createMockSubscription();
        when(subscriptionRepository.findById(10L)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.handle(new RequestPlanChangeCommand(10L, 1L, "CUSTOM"));

        assertEquals("CUSTOM", result.getPendingPlanCode());
        verify(subscriptionRepository).save(subscription);
    }
}
