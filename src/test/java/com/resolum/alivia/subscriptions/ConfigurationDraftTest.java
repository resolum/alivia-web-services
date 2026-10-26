package com.resolum.alivia.subscriptions;

import com.resolum.alivia.subscriptions.domain.model.entities.ConfigurationDraft;
import com.resolum.alivia.subscriptions.domain.model.entities.ConfiguredRoom;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PaymentStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripeAuthorization;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.StripePaymentIntentId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationDraftTest {
    @Test
    void storesRoomsAndAggregatesActuatorsForPricing() {
        var draft = new ConfigurationDraft(PlanCode.CUSTOM, "MONTHLY");
        draft.replaceRooms(List.of(
                new ConfiguredRoom("main", "Dormitorio principal", 2, 1, 0, 0, 1),
                new ConfiguredRoom("second", "Dormitorio secundario", 1, 0, 0, 0, 1)));

        assertEquals(2, draft.getRooms().size());
        assertEquals(3, draft.actuatorQuantities().lights());
        assertEquals(1, draft.actuatorQuantities().doors());
        assertEquals(4, draft.actuatorQuantities().toSelection().total());
    }

    @Test
    void rejectsDuplicateRoomsAndMissingMicrophone() {
        var draft = new ConfigurationDraft(PlanCode.CUSTOM, "ANNUAL");
        var room = new ConfiguredRoom("main", "Dormitorio", 1, 0, 0, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> draft.replaceRooms(List.of(room, room)));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfiguredRoom("other", "Otro", 0, 0, 0, 0, 0));
    }

    @Test
    void enforcesTheActuatorLimitDefinedByEachPlan() {
        var custom = new ConfigurationDraft(PlanCode.CUSTOM, "MONTHLY");
        assertThrows(IllegalArgumentException.class, () -> custom.replaceRooms(List.of(
                new ConfiguredRoom("main", "Dormitorio", 3, 2, 0, 0, 1))));

        var minimum = new ConfigurationDraft(PlanCode.MINIMUM, "MONTHLY");
        assertThrows(IllegalArgumentException.class, () -> minimum.replaceRooms(List.of(
                new ConfiguredRoom("main", "Dormitorio", 1, 1, 0, 0, 1))));
    }

    @Test
    void ownsPaymentSetupAndPreauthorizationBeforeAnAccountExists() {
        var draft = new ConfigurationDraft(PlanCode.MINIMUM, "MONTHLY");
        draft.linkStripeCustomer("cus_draft");
        draft.recordPaymentSetup("seti_draft");
        var deadline = Instant.parse("2026-10-30T12:00:00Z");
        draft.recordPreauthorization(new StripeAuthorization(
                new StripePaymentIntentId("pi_draft"), "secret", PaymentStatus.PREAUTHORIZED, deadline));

        assertEquals("cus_draft", draft.getStripeCustomerId());
        assertEquals("seti_draft", draft.getStripeSetupIntentId());
        assertEquals("pi_draft", draft.getStripePaymentIntentId());
        assertEquals(PaymentStatus.PREAUTHORIZED, draft.getPaymentStatus());
        assertEquals(deadline, draft.getCaptureBefore());
    }
}
