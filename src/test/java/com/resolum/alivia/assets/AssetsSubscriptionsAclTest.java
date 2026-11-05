package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.outboundservices.acl.ExternalSubscriptionsService;
import com.resolum.alivia.assets.domain.exceptions.DeviceIncompatibleWithPlan;
import com.resolum.alivia.assets.domain.exceptions.DeviceNotAvailable;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.subscriptions.application.acl.SubscriptionsContextFacadeImpl;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorType;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The read-only Subscriptions view that Assets uses, and how the new errors are answered. */
class AssetsSubscriptionsAclTest {

    private static Subscription subscription(boolean active, Map<ActuatorType, Integer> actuators) {
        var subscription = mock(Subscription.class);
        when(subscription.isActive()).thenReturn(active);
        when(subscription.getActuators()).thenReturn(new EnumMap<>(actuators));
        return subscription;
    }

    @Test
    void theFacadeReportsTheActuatorsOfTheActiveSubscriptionOfTheHousehold() {
        var repository = mock(SubscriptionRepository.class);
        var active = subscription(true, Map.of(ActuatorType.LIGHT, 2, ActuatorType.DOOR, 1));
        when(repository.findByActiveHouseholdId("5")).thenReturn(Optional.of(active));
        var facade = new SubscriptionsContextFacadeImpl(null, repository, null, Clock.systemUTC());

        var contracted = facade.fetchContractedActuatorsOfHousehold(" 5 ").orElseThrow();

        assertEquals(Map.of("LIGHT", 2, "DOOR", 1), contracted.quantityByType());
    }

    @Test
    void theFacadeAnswersEmptyWithoutAnActiveSubscription() {
        var repository = mock(SubscriptionRepository.class);
        var canceled = subscription(false, Map.of(ActuatorType.LIGHT, 2));
        when(repository.findByActiveHouseholdId("5")).thenReturn(Optional.of(canceled));
        when(repository.findByActiveHouseholdId("6")).thenReturn(Optional.empty());
        var facade = new SubscriptionsContextFacadeImpl(null, repository, null, Clock.systemUTC());

        assertTrue(facade.fetchContractedActuatorsOfHousehold("5").isEmpty());
        assertTrue(facade.fetchContractedActuatorsOfHousehold("6").isEmpty());
        assertTrue(facade.fetchContractedActuatorsOfHousehold(" ").isEmpty());
        assertTrue(facade.fetchContractedActuatorsOfHousehold(null).isEmpty());
    }

    @Test
    void assetsAsksWithTheNumericIdAsTextAndKeepsOnlyTheTypesItManages() {
        var facade = mock(SubscriptionsContextFacade.class);
        when(facade.fetchContractedActuatorsOfHousehold("5")).thenReturn(Optional.of(
                new SubscriptionsContextFacade.ContractedActuators(Map.of("LIGHT", 2, "CURTAIN", 1, "SIREN", 4))));
        var service = new ExternalSubscriptionsService(facade);

        var contracted = service.fetchContractedActuators(5L).orElseThrow();

        assertEquals(2, contracted.quantityOf(DeviceType.LIGHT));
        assertEquals(1, contracted.quantityOf(DeviceType.CURTAIN));
        assertEquals(0, contracted.quantityOf(DeviceType.DOOR));
        assertEquals(2, contracted.quantityByType().size());
    }

    @Test
    void assetsAnswersEmptyWhenTheHouseholdHasNoActiveSubscription() {
        var facade = mock(SubscriptionsContextFacade.class);
        when(facade.fetchContractedActuatorsOfHousehold(any())).thenReturn(Optional.empty());

        assertTrue(new ExternalSubscriptionsService(facade).fetchContractedActuators(5L).isEmpty());
    }

    @Test
    void answers409WithADistinctCodeWhenTheDeviceIsAlreadyAssignedOrJustUnavailable() {
        var handler = new GlobalExceptionHandler();

        var assigned = handler.handleDeviceNotAvailable(new DeviceNotAvailable(7L, AssetStatus.IN_USE));
        var broken = handler.handleDeviceNotAvailable(new DeviceNotAvailable(7L, AssetStatus.BROKEN));

        assertEquals(HttpStatus.CONFLICT.value(), assigned.getStatus());
        assertEquals("device_already_assigned", assigned.getProperties().get("code"));
        assertEquals(HttpStatus.CONFLICT.value(), broken.getStatus());
        assertEquals("device_not_available", broken.getProperties().get("code"));
    }

    @Test
    void answers422WhenTheDeviceDoesNotFitThePlan() {
        var problem = new GlobalExceptionHandler()
                .handleDeviceIncompatibleWithPlan(new DeviceIncompatibleWithPlan("The plan does not include DOOR devices."));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), problem.getStatus());
        assertEquals("device_incompatible_with_plan", problem.getProperties().get("code"));
    }
}
