package com.resolum.alivia.subscriptions;

import com.resolum.alivia.subscriptions.application.internal.commandservices.ConfigurationDraftPaymentService;
import com.resolum.alivia.subscriptions.application.internal.commandservices.ConfigurationDraftService;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfiguredRoom;
import com.resolum.alivia.subscriptions.domain.model.factories.PlanCatalogDefaults;
import com.resolum.alivia.subscriptions.domain.model.queries.GetPlanByCodeQuery;
import com.resolum.alivia.subscriptions.domain.model.queries.QuotePlanQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentSetup;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripeAuthorization;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import com.resolum.alivia.subscriptions.domain.services.PlanQueryService;
import com.resolum.alivia.subscriptions.interfaces.rest.controllers.ConfigurationDraftPaymentsController;
import com.resolum.alivia.subscriptions.interfaces.rest.controllers.ConfigurationDraftsController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfigurationDraftControllersTest {
    private ConfigurationDraftService drafts;
    private PlanQueryService plans;
    private ConfigurationDraftsController controller;

    @BeforeEach
    void setUp() {
        drafts = mock(ConfigurationDraftService.class);
        plans = mock(PlanQueryService.class);
        controller = new ConfigurationDraftsController(drafts, plans);
        var custom = PlanCatalogDefaults.custom();
        when(plans.handle(any(GetPlanByCodeQuery.class))).thenReturn(custom);
        when(plans.handle(any(QuotePlanQuery.class))).thenAnswer(call -> {
            QuotePlanQuery query = call.getArgument(0);
            return custom.quoteMonthly(query.rooms(), query.quantities().toSelection());
        });
    }

    private static ConfigurationDraft draftWith(ConfiguredRoom... rooms) {
        var draft = new ConfigurationDraft(PlanCode.CUSTOM, "MONTHLY");
        if (rooms.length > 0) draft.replaceRooms(List.of(rooms));
        return draft;
    }

    @Test
    void createsADraftForAnActivePlanWithoutQuoteWhileEmpty() {
        var draft = draftWith();
        when(drafts.create(PlanCode.CUSTOM, "MONTHLY")).thenReturn(draft);

        var resource = controller.create(new ConfigurationDraftsController.CreateDraftRequest("CUSTOM", "MONTHLY"));

        assertEquals(draft.getId(), resource.id());
        assertEquals(PlanCode.CUSTOM, resource.planCode());
        assertTrue(resource.rooms().isEmpty());
        assertNull(resource.quote());
        verify(plans).handle(new GetPlanByCodeQuery(PlanCode.CUSTOM));
    }

    @Test
    void returnsTheQuoteOnceTheSelectionSatisfiesThePlan() {
        var draft = draftWith(
                new ConfiguredRoom("main", "Dormitorio principal", 1, 1, 0, 0, 1),
                new ConfiguredRoom("second", "Dormitorio secundario", 1, 0, 0, 0, 1));
        when(drafts.find(draft.getId())).thenReturn(draft);

        var resource = controller.get(draft.getId());

        assertNotNull(resource.quote());
        assertEquals(2, resource.rooms().size());
        var room = resource.rooms().getFirst();
        assertEquals("main", room.key());
        assertEquals("Dormitorio principal", room.name());
        assertEquals(1, room.lights());
        assertEquals(1, room.doors());
        assertEquals(1, room.microphones());
    }

    @Test
    void omitsTheQuoteWhenTheSelectionIsBelowThePlanRules() {
        var draft = draftWith(new ConfiguredRoom("main", "Dormitorio", 0, 0, 0, 0, 1));
        when(drafts.find(draft.getId())).thenReturn(draft);

        assertNull(controller.get(draft.getId()).quote());
        verify(plans, never()).handle(any(QuotePlanQuery.class));
    }

    @Test
    void replacesRoomsAndRejectsAMissingRoomList() {
        var draft = draftWith(new ConfiguredRoom("main", "Dormitorio", 1, 0, 0, 0, 1));
        when(drafts.replaceRooms(eq(draft.getId()), anyList())).thenReturn(draft);
        var request = new ConfigurationDraftsController.ReplaceRoomsRequest(List.of(
                new ConfigurationDraftsController.RoomRequest("main", "Dormitorio", 1, 0, 0, 0, 1)));

        var resource = controller.replaceRooms(draft.getId(), request);

        assertEquals(1, resource.rooms().size());
        verify(drafts).replaceRooms(eq(draft.getId()), argThat(rooms -> rooms.size() == 1
                && rooms.getFirst().getKey().equals("main") && rooms.getFirst().getLights() == 1));
        assertThrows(IllegalArgumentException.class, () -> controller.replaceRooms(draft.getId(),
                new ConfigurationDraftsController.ReplaceRoomsRequest(null)));
    }

    @Test
    void paymentsControllerExposesSetupPreauthorizationAndStatus() {
        var payments = mock(ConfigurationDraftPaymentService.class);
        var paymentsController = new ConfigurationDraftPaymentsController(payments);
        var key = UUID.randomUUID();
        var deadline = Instant.parse("2026-11-02T12:00:00Z");
        var authorization = new StripeAuthorization(new StripePaymentIntentId("pi_1"), "secret",
                PaymentStatus.PREAUTHORIZED, deadline);
        when(payments.prepare("draft", key)).thenReturn(new PaymentSetup("seti_1", "seti_secret", "pk_test"));
        when(payments.preauthorize("draft", "pm_card", key)).thenReturn(authorization);
        when(payments.status("draft")).thenReturn(authorization);

        var setup = paymentsController.prepare("draft", key);
        assertEquals("seti_1", setup.setupIntentId());
        assertEquals("pk_test", setup.publishableKey());

        var created = paymentsController.preauthorize("draft", key,
                new ConfigurationDraftPaymentsController.PreauthorizationRequest("pm_card"));
        assertEquals("draft", created.draftId());
        assertEquals("PREAUTHORIZED", created.status());
        assertEquals("pi_1", created.stripePaymentIntentId());
        assertEquals("secret", created.clientSecret());
        assertEquals(deadline, created.captureBefore());

        assertEquals("pi_1", paymentsController.status("draft").stripePaymentIntentId());
    }
}
