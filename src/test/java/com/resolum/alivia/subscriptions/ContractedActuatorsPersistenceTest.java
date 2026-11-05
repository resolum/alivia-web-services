package com.resolum.alivia.subscriptions;

import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.subscriptions.application.acl.SubscriptionsContextFacadeImpl;
import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Subscription;
import com.resolum.alivia.subscriptions.domain.model.factories.PlanCatalogDefaults;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorQuantity;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorSelection;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorType;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.BillingPeriod;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.HouseholdId;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.Money;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.UserId;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.PlanRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The contracted actuators are read from a stored subscription, through the facade's own transaction. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED) // the facade must open its own transaction to read the lazy actuators
@Import({SubscriptionsContextFacadeImpl.class, ClockConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:contracted;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // The migrations are PostgreSQL DDL; this slice builds its schema with Hibernate.
        "spring.flyway.enabled=false"})
class ContractedActuatorsPersistenceTest {

    @Autowired
    private SubscriptionsContextFacade facade;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private PlanRepository plans;

    @MockitoBean
    private PaymentAuthorizationGateway authorizations;

    @AfterEach
    void cleanDatabase() {
        subscriptions.deleteAllInBatch();
        plans.deleteAllInBatch();
    }

    private Subscription stored(String household, boolean active) {
        var plan = plans.save(PlanCatalogDefaults.custom());
        var selection = new ActuatorSelection(List.of(
                new ActuatorQuantity(ActuatorType.LIGHT, 2), new ActuatorQuantity(ActuatorType.DOOR, 1)));
        var subscription = new Subscription(new UserId(9L), new HouseholdId(household), plan, selection,
                new Money(new BigDecimal("99.00"), "PEN"), "cus_test", "idem-" + household);
        if (active) {
            subscription.linkStripeSubscription("sub_test");
            subscription.activate(new BillingPeriod(LocalDateTime.now(), LocalDateTime.now().plusMonths(1)));
        }
        return subscriptions.save(subscription);
    }

    @Test
    void readsTheContractedQuantitiesOfTheActiveSubscription() {
        stored("5", true);

        var contracted = facade.fetchContractedActuatorsOfHousehold("5").orElseThrow();

        assertEquals(Map.of("LIGHT", 2, "DOOR", 1), contracted.quantityByType());
    }

    @Test
    void ignoresASubscriptionThatIsNotActive() {
        stored("6", false);

        assertTrue(facade.fetchContractedActuatorsOfHousehold("6").isEmpty());
    }

    @Test
    void ignoresAHouseholdWithoutASubscription() {
        assertTrue(facade.fetchContractedActuatorsOfHousehold("7").isEmpty());
    }
}
