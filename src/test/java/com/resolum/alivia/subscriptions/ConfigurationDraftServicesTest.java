package com.resolum.alivia.subscriptions;

import com.resolum.alivia.subscriptions.application.internal.commandservices.ConfigurationDraftPaymentService;
import com.resolum.alivia.subscriptions.application.internal.commandservices.ConfigurationDraftService;
import com.resolum.alivia.subscriptions.application.internal.commandservices.ExpiredDraftPreauthorizationReleaser;
import com.resolum.alivia.subscriptions.application.outboundservices.PaymentAuthorizationGateway;
import com.resolum.alivia.subscriptions.application.outboundservices.StripeService;
import com.resolum.alivia.subscriptions.domain.exceptions.ConfigurationDraftNotFound;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfiguredRoom;
import com.resolum.alivia.subscriptions.domain.model.factories.PlanCatalogDefaults;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetup;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripeAuthorization;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.infrastructure.persistence.jpa.repositories.ConfigurationDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfigurationDraftServicesTest {
    private ConfigurationDraftRepository drafts;
    private PlanQueryService plans;
    private StripeService stripe;
    private PaymentAuthorizationGateway gateway;
    private ConfigurationDraftPaymentService payments;

    @BeforeEach
    void setUp() {
        drafts = mock(ConfigurationDraftRepository.class);
        plans = mock(PlanQueryService.class);
        stripe = mock(StripeService.class);
        gateway = mock(PaymentAuthorizationGateway.class);
        payments = new ConfigurationDraftPaymentService(drafts, plans, stripe, gateway);
        when(plans.handle(any(GetPlanByCodeQuery.class))).thenReturn(PlanCatalogDefaults.custom());
    }

    private static ConfigurationDraft customDraft() {
        var draft = new ConfigurationDraft(PlanCode.CUSTOM, "MONTHLY");
        draft.replaceRooms(List.of(
                new ConfiguredRoom("main", "Dormitorio principal", 1, 1, 0, 0, 1),
                new ConfiguredRoom("second", "Dormitorio secundario", 1, 0, 0, 0, 1)));
        return draft;
    }

    private static StripeAuthorization authorization(String intentId, PaymentStatus state) {
        return new StripeAuthorization(new StripePaymentIntentId(intentId), "secret", state,
                Instant.parse("2026-11-02T12:00:00Z"));
    }

    private ConfigurationDraft stored(ConfigurationDraft draft) {
        when(drafts.findById(draft.getId())).thenReturn(Optional.of(draft));
        return draft;
    }

    @Test
    void draftServiceCreatesFindsAndReplacesRooms() {
        var service = new ConfigurationDraftService(drafts);
        when(drafts.save(any(ConfigurationDraft.class))).thenAnswer(call -> call.getArgument(0));

        var created = service.create(PlanCode.CUSTOM, "MONTHLY");
        assertEquals(PlanCode.CUSTOM, created.getPlanCode());

        stored(created);
        assertSame(created, service.find(created.getId()));

        var updated = service.replaceRooms(created.getId(),
                List.of(new ConfiguredRoom("main", "Dormitorio", 1, 0, 0, 0, 1)));
        assertEquals(1, updated.getRooms().size());
        verify(drafts, times(2)).save(created);
    }

    @Test
    void draftServiceRejectsUnknownDrafts() {
        var service = new ConfigurationDraftService(drafts);
        when(drafts.findById("missing")).thenReturn(Optional.empty());

        var error = assertThrows(ConfigurationDraftNotFound.class, () -> service.find("missing"));
        assertTrue(error.getMessage().contains("missing"));
    }

    @Test
    void prepareCreatesCustomerOnceAndRecordsTheSetupIntent() {
        var draft = stored(customDraft());
        var key = UUID.randomUUID();
        when(stripe.createCheckoutCustomer(draft.getId())).thenReturn("cus_new");
        when(stripe.createPaymentSetup("cus_new", key)).thenReturn(new PaymentSetup("seti_1", "secret", "pk_test"));

        var setup = payments.prepare(draft.getId(), key);

        assertEquals("seti_1", setup.setupIntentId());
        assertEquals("cus_new", draft.getStripeCustomerId());
        assertEquals("seti_1", draft.getStripeSetupIntentId());
        verify(drafts).saveAndFlush(draft);

        when(stripe.createPaymentSetup("cus_new", key)).thenReturn(new PaymentSetup("seti_2", "secret", "pk_test"));
        payments.prepare(draft.getId(), key);
        verify(stripe, times(1)).createCheckoutCustomer(anyString());
    }

    @Test
    void prepareRequiresAnIdempotencyKey() {
        assertThrows(IllegalArgumentException.class, () -> payments.prepare("draft", null));
        verifyNoInteractions(drafts, stripe);
    }

    @Test
    void preauthorizeChargesTheAmountQuotedFromTheStoredRooms() {
        var draft = stored(customDraft());
        draft.linkStripeCustomer("cus_1");
        draft.recordPaymentSetup("seti_1");
        var key = UUID.randomUUID();
        var expected = PlanCatalogDefaults.custom()
                .quoteMonthly(2, draft.actuatorQuantities().toSelection())
                .totalMonthlyAmount().movePointRight(2).longValueExact();
        when(gateway.create(eq("cus_1"), eq("pm_card"), eq(expected), anyString(), eq(key)))
                .thenReturn(authorization("pi_1", PaymentStatus.PREAUTHORIZED));

        var result = payments.preauthorize(draft.getId(), "pm_card", key);

        assertEquals(PaymentStatus.PREAUTHORIZED, result.state());
        assertEquals("pi_1", draft.getStripePaymentIntentId());
        verify(drafts).saveAndFlush(draft);
    }

    @Test
    void preauthorizeReturnsTheExistingIntentInsteadOfChargingTwice() {
        var draft = stored(customDraft());
        draft.recordPreauthorization(authorization("pi_existing", PaymentStatus.AWAITING_CONFIRMATION));
        when(gateway.retrieve(new StripePaymentIntentId("pi_existing")))
                .thenReturn(authorization("pi_existing", PaymentStatus.PREAUTHORIZED));

        var result = payments.preauthorize(draft.getId(), "pm_card", UUID.randomUUID());

        assertEquals(PaymentStatus.PREAUTHORIZED, result.state());
        assertEquals(PaymentStatus.PREAUTHORIZED, draft.getPaymentStatus());
        verify(gateway, never()).create(any(), any(), anyLong(), any(), any());
    }

    @Test
    void preauthorizeValidatesInputAndPaymentSetup() {
        assertThrows(IllegalArgumentException.class, () -> payments.preauthorize("d", "pm_card", null));
        assertThrows(IllegalArgumentException.class, () -> payments.preauthorize("d", null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> payments.preauthorize("d", "card", UUID.randomUUID()));

        var draft = stored(customDraft());
        assertThrows(IllegalStateException.class,
                () -> payments.preauthorize(draft.getId(), "pm_card", UUID.randomUUID()));
        draft.linkStripeCustomer("cus_1");
        assertThrows(IllegalStateException.class,
                () -> payments.preauthorize(draft.getId(), "pm_card", UUID.randomUUID()));
    }

    @Test
    void preauthorizeCancelsTheHoldWhenItCannotBeSaved() {
        var draft = stored(customDraft());
        draft.linkStripeCustomer("cus_1");
        draft.recordPaymentSetup("seti_1");
        when(gateway.create(any(), any(), anyLong(), any(), any()))
                .thenReturn(authorization("pi_1", PaymentStatus.PREAUTHORIZED));
        var failure = new IllegalStateException("database down");
        when(drafts.saveAndFlush(draft)).thenThrow(failure);
        doThrow(new IllegalStateException("stripe down")).when(gateway).cancel(new StripePaymentIntentId("pi_1"));

        var thrown = assertThrows(IllegalStateException.class,
                () -> payments.preauthorize(draft.getId(), "pm_card", UUID.randomUUID()));

        assertSame(failure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        verify(gateway).cancel(new StripePaymentIntentId("pi_1"));
    }

    @Test
    void statusRefreshesTheStoredPreauthorization() {
        var draft = stored(customDraft());
        assertThrows(IllegalStateException.class, () -> payments.status(draft.getId()));

        draft.recordPreauthorization(authorization("pi_1", PaymentStatus.AWAITING_CONFIRMATION));
        when(gateway.retrieve(new StripePaymentIntentId("pi_1")))
                .thenReturn(authorization("pi_1", PaymentStatus.PREAUTHORIZED));

        assertEquals(PaymentStatus.PREAUTHORIZED, payments.status(draft.getId()).state());
        verify(drafts).save(draft);
    }

    @Test
    void releaserCancelsOnlyExpiredHolds() {
        var expired = customDraft();
        expired.recordPreauthorization(new StripeAuthorization(new StripePaymentIntentId("pi_old"), null,
                PaymentStatus.PREAUTHORIZED, Instant.now().minusSeconds(3600)));
        var recent = customDraft();
        recent.recordPreauthorization(new StripeAuthorization(new StripePaymentIntentId("pi_new"), null,
                PaymentStatus.PREAUTHORIZED, Instant.now().plusSeconds(6 * 24 * 3600L)));
        when(drafts.findByPaymentStatus(PaymentStatus.PREAUTHORIZED)).thenReturn(List.of(expired, recent));

        new ExpiredDraftPreauthorizationReleaser(drafts, gateway).releaseExpired();

        verify(gateway).cancel(new StripePaymentIntentId("pi_old"));
        verify(gateway, never()).cancel(new StripePaymentIntentId("pi_new"));
        assertEquals(PaymentStatus.CANCELED, expired.getPaymentStatus());
        assertEquals(PaymentStatus.PREAUTHORIZED, recent.getPaymentStatus());
        verify(drafts).save(expired);
    }
}
