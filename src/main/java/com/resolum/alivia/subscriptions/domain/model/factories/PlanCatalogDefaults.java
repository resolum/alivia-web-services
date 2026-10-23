package com.resolum.alivia.subscriptions.domain.model.factories;

import com.resolum.alivia.subscriptions.domain.model.aggregates.Plan;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorType;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.Money;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PriceQualifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Defines the plans loaded when the catalog is empty. */
public final class PlanCatalogDefaults {
    private PlanCatalogDefaults() {
    }

    public static Plan base() {
        return new Plan(
                PlanCode.MINIMUM, "Plan Base",
                "Asistencia inicial en una habitación con un actuador a elección.",
                pen("99.00"), PriceQualifier.FIXED, null,
                1, 1, 1, 1, Map.of(),
                List.of("Una habitación y un actuador a elección", "Control por voz",
                        "App Alivia para cuidadores y familiares", "Evaluación técnica sin costo"),
                List.of("Micrófono", "Un actuador a elección")
        );
    }

    public static Plan custom() {
        return new Plan(
                PlanCode.CUSTOM, "Plan Personalizado",
                "Configura varias habitaciones y de uno a cuatro actuadores.",
                pen("149.00"), PriceQualifier.BASE_COMPONENT, pen("35.00"),
                2, 1, 4, 0,
                Map.of(
                        ActuatorType.LIGHT, pen("15.00"),
                        ActuatorType.DOOR, pen("25.00"),
                        ActuatorType.WINDOW, pen("25.00"),
                        ActuatorType.CURTAIN, pen("20.00")
                ),
                List.of("Dos habitaciones en el precio base; S/ 35 por cada habitación adicional",
                        "Uno a cuatro actuadores a medida", "Nombres por habitación para control por voz",
                        "App con múltiples cuidadores y alertas zonales", "Evaluación técnica sin recargo"),
                List.of("Micrófono")
        );
    }

    private static Money pen(String amount) {
        return new Money(new BigDecimal(amount), "PEN");
    }
}
