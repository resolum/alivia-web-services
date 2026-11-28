package com.resolum.alivia.subscriptions;

import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import com.resolum.alivia.subscriptions.application.acl.SubscriptionsContextFacadeImpl;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfiguredRoom;
import com.resolum.alivia.subscriptions.domain.model.factories.PlanCatalogDefaults;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripeAuthorization;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.PlanRepository;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.SubscriptionRepository;
import com.resolum.alivia.subscriptions.interfaces.acl.SubscriptionsContextFacade.Sale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** TS-31: the sales analytics reads are the contracting requests whose payment was authorized, over a real JPA. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:sales;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"})
class SalesFacadePersistenceTest {

    @Autowired
    private ConfigurationDraftRepository drafts;
    @Autowired
    private PlanRepository plans;
    @Autowired
    private SubscriptionRepository subscriptions;

    private ConfigurationDraft draft(String cycle, PaymentStatus status, int intent) {
        var draft = new ConfigurationDraft(PlanCode.MINIMUM, cycle);
        draft.replaceRooms(List.of(new ConfiguredRoom("sala", "Sala", 1, 0, 0, 0, 1)));
        draft.recordPreauthorization(new StripeAuthorization(new StripePaymentIntentId("pi_" + intent), null,
                PaymentStatus.PREAUTHORIZED, null));
        if (status == PaymentStatus.SUCCEEDED) draft.confirmCapturedPayment();
        if (status == PaymentStatus.CANCELED) draft.markPreauthorizationCanceled();
        return drafts.saveAndFlush(draft);
    }

    @Test
    void authorizedRequestsAreHiringsAndOnlyCapturedOnesAreSales() {
        var plan = plans.saveAndFlush(PlanCatalogDefaults.base());
        draft("MONTHLY", PaymentStatus.SUCCEEDED, 1);
        draft("ANNUAL", PaymentStatus.PREAUTHORIZED, 2);
        draft("MONTHLY", PaymentStatus.CANCELED, 3);
        var facade = new SubscriptionsContextFacadeImpl(drafts, subscriptions, null, Clock.systemUTC(), plans);
        var now = Instant.now();

        List<Sale> sales = facade.fetchSales(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)), null);

        assertThat(sales).hasSize(2);
        assertThat(sales).extracting(Sale::billingCycle).containsExactlyInAnyOrder("MONTHLY", "ANNUAL");
        assertThat(sales).filteredOn(Sale::paid).singleElement()
                .satisfies(sale -> {
                    assertThat(sale.planId()).isEqualTo(plan.getId());
                    assertThat(sale.amount()).isEqualByComparingTo(new BigDecimal("99.00"));
                    assertThat(sale.currency()).isEqualTo("PEN");
                });
        assertThat(facade.fetchSales(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)), plan.getId() + 1))
                .isEmpty();
        assertThat(facade.countSubscriptionsByStatus()).isEmpty();
    }
}
