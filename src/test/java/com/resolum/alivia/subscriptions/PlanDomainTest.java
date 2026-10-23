package com.resolum.alivia.subscriptions;

import com.resolum.alivia.subscriptions.domain.exceptions.InvalidActuatorSelection;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidMoney;
import com.resolum.alivia.subscriptions.domain.exceptions.InvalidPlan;
import com.resolum.alivia.subscriptions.domain.exceptions.PlanNotFound;
import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.domain.model.factories.PlanCatalogDefaults;
import com.resolum.alivia.subscriptions.domain.model.queries.GetAllPlansQuery;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorQuantities;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorQuantity;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorSelection;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorType;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.Money;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanStatus;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PriceQualifier;
import com.resolum.alivia.subscriptions.interfaces.rest.transform.PlanQuoteResourceFromResultsAssembler;
import com.resolum.alivia.subscriptions.interfaces.rest.transform.PlanResourceFromEntityAssembler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanDomainTest {
    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    @Test
    void basePlanHasFixedPriceAndExactlyOneActuator() {
        Plan base = PlanCatalogDefaults.base();
        ActuatorSelection oneDoor = new ActuatorQuantities(0, 1, 0, 0).toSelection();

        assertEquals(PlanCode.MINIMUM, base.getCode());
        assertEquals(PlanStatus.ACTIVE, base.getStatus());
        assertEquals(PriceQualifier.FIXED, base.getPriceQualifier());
        assertEquals(amount("99.00"), base.minimumMonthlyPrice().amount());
        assertEquals(amount("99.00"), base.monthlyPriceFor(oneDoor).amount());
        assertTrue(base.allows(oneDoor));
        assertFalse(base.allows(null));
        assertFalse(base.allows(new ActuatorQuantities(1, 1, 0, 0).toSelection()));
        assertNull(base.getAdditionalRoomPrice());

        var quote = base.quoteMonthly(1, oneDoor);
        assertEquals(1, quote.rooms());
        assertEquals(1, quote.deviceQuantities().get(ActuatorType.DOOR));
        assertEquals(amount("0.00"), quote.devicesMonthlyAmount());
        assertEquals(amount("99.00"), quote.totalMonthlyAmount());
        assertThrows(InvalidActuatorSelection.class, () -> base.quoteMonthly(2, oneDoor));
        assertThrows(InvalidActuatorSelection.class,
                () -> base.quoteMonthly(1, new ActuatorQuantities(0, 0, 0, 0).toSelection()));

        var resource = PlanResourceFromEntityAssembler.toResourceFromEntity(base);
        assertEquals("MONTHLY", resource.billingPeriod());
        assertEquals(amount("99.00"), resource.minimumMonthlyAmount());
        assertNull(resource.additionalRoomMonthlyAmount());
        assertEquals(1, resource.includedActuators());
        assertFalse(resource.features().isEmpty());
    }

    @Test
    void customPlanAddsDeviceAndExtraRoomPrices() {
        Plan custom = PlanCatalogDefaults.custom();
        ActuatorSelection devices = new ActuatorQuantities(2, 1, 0, 1).toSelection();

        assertEquals(amount("149.00"), custom.getBasePrice().amount());
        assertEquals(amount("164.00"), custom.minimumMonthlyPrice().amount());
        assertEquals(amount("35.00"), custom.getAdditionalRoomPrice().amount());
        assertEquals(4, custom.getDeviceMonthlyPrices().size());
        assertEquals(4, devices.total());

        var quote = custom.quoteMonthly(3, devices);
        assertEquals(amount("149.00"), quote.baseMonthlyAmount());
        assertEquals(amount("35.00"), quote.additionalRoomsMonthlyAmount());
        assertEquals(amount("75.00"), quote.devicesMonthlyAmount());
        assertEquals(amount("259.00"), quote.totalMonthlyAmount());
        assertEquals(amount("164.00"), custom.monthlyPriceFor(new ActuatorQuantities(1, 0, 0, 0).toSelection()).amount());
        assertEquals(2, quote.deviceQuantities().get(ActuatorType.LIGHT));

        var resource = PlanResourceFromEntityAssembler.toResourceFromEntity(custom);
        assertEquals(amount("164.00"), resource.minimumMonthlyAmount());
        assertEquals(amount("35.00"), resource.additionalRoomMonthlyAmount());
        assertEquals(PriceQualifier.BASE_COMPONENT, resource.priceQualifier());
        assertEquals(2, resource.minimumRooms());

        var quoteResource = PlanQuoteResourceFromResultsAssembler.toResourceFromResult(quote);
        assertEquals("MONTHLY", quoteResource.billingPeriod());
        assertEquals(amount("259.00"), quoteResource.totalMonthlyAmount());
        assertThrows(InvalidActuatorSelection.class, () -> custom.quoteMonthly(1, devices));
        assertThrows(InvalidActuatorSelection.class,
                () -> custom.quoteMonthly(2, new ActuatorQuantities(2, 2, 1, 0).toSelection()));
    }

    @Test
    void catalogDoesNotAllowExternalMutation() {
        Plan custom = PlanCatalogDefaults.custom();
        assertThrows(UnsupportedOperationException.class, () -> custom.getFeatures().add("extra"));
        assertThrows(UnsupportedOperationException.class, () -> custom.getIncludedDevices().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> custom.getDeviceMonthlyPrices().put(ActuatorType.LIGHT, amount("1.00")));
    }

    @Test
    void rejectsInvalidPlanConfiguration() {
        Money base = new Money(amount("99"), "PEN");
        assertThrows(InvalidPlan.class, () -> new Plan(null, "Base", "Descripción", base,
                PriceQualifier.FIXED, null, 1, 1, 1, 1, Map.of(), List.of(), List.of()));
        assertThrows(InvalidPlan.class, () -> new Plan(PlanCode.MINIMUM, "Base", "Descripción", base,
                PriceQualifier.FIXED, null, 2, 1, 1, 1, Map.of(), List.of(), List.of()));
        assertThrows(InvalidPlan.class, () -> new Plan(PlanCode.CUSTOM, "Custom", "Descripción", base,
                PriceQualifier.BASE_COMPONENT, new Money(amount("35"), "PEN"), 2, 1, 4, 0,
                Map.of(ActuatorType.LIGHT, new Money(amount("15"), "PEN")), List.of(), List.of()));
        assertThrows(InvalidPlan.class, () -> new Plan(PlanCode.MINIMUM, " ", "Descripción", base,
                PriceQualifier.FIXED, null, 1, 1, 1, 1, Map.of(), List.of(), List.of()));
    }

    @Test
    void validatesSelectionsAndMoney() {
        assertEquals(4, new ActuatorQuantities(1, 1, 1, 1).toSelection().total());
        assertEquals(1, new ActuatorQuantities(1, 1, 1, 1).toSelection().quantityOf(ActuatorType.WINDOW));
        assertEquals(0, new ActuatorQuantities(1, 0, 0, 0).toSelection().quantityOf(ActuatorType.DOOR));
        assertThrows(InvalidActuatorSelection.class, () -> new ActuatorQuantities(-1, 0, 0, 0));
        assertThrows(InvalidActuatorSelection.class, () -> new ActuatorQuantity(null, 1));
        assertThrows(InvalidActuatorSelection.class, () -> new ActuatorQuantity(ActuatorType.LIGHT, 0));
        assertThrows(InvalidActuatorSelection.class, () -> new ActuatorSelection(null));
        assertThrows(InvalidActuatorSelection.class,
                () -> new ActuatorSelection(List.of(new ActuatorQuantity(ActuatorType.LIGHT, 1),
                        new ActuatorQuantity(ActuatorType.LIGHT, 2))));

        Money price = new Money(amount("15"), "PEN");
        assertEquals(amount("45.00"), price.times(3).amount());
        assertEquals(amount("30.00"), price.plus(price).amount());
        assertThrows(InvalidMoney.class, () -> new Money(amount("-1"), "PEN"));
        assertThrows(InvalidMoney.class, () -> new Money(amount("1"), "USD"));
        assertThrows(InvalidMoney.class, () -> price.times(-1));
        assertThrows(NullPointerException.class, () -> price.plus(null));
    }

    @Test
    void acceptsOnlyKnownActivePlans() {
        assertEquals(PlanCode.CUSTOM, PlanCode.from("custom"));
        assertEquals(PlanCode.MINIMUM, PlanCode.from("MINIMUM"));
        assertThrows(PlanNotFound.class, () -> PlanCode.from("UNKNOWN"));
        assertThrows(PlanNotFound.class, () -> PlanCode.from(null));
        assertEquals(PlanStatus.ACTIVE, new GetAllPlansQuery(PlanStatus.ACTIVE).status());
        assertThrows(InvalidPlan.class, () -> new GetAllPlansQuery(null));
    }
}
