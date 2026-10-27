package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.ActuatorType;
import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

@Schema(name = "CotizacionPlan",
        description = "Desglose mensual calculado por el backend para una selección de habitaciones y actuadores")
public record PlanQuoteResource(
        @Schema(description = "Código del plan cotizado", example = "CUSTOM")
        PlanCode planCode,
        @Schema(description = "Moneda ISO 4217", example = "PEN")
        String currency,
        @Schema(description = "Periodo utilizado por la cotización", example = "MONTHLY")
        String billingPeriod,
        @Schema(description = "Cantidad de habitaciones", example = "2")
        int rooms,
        @Schema(description = "Cantidades agregadas por tipo de actuador")
        Map<ActuatorType, Integer> deviceQuantities,
        @Schema(description = "Importe mensual base", example = "149.00")
        BigDecimal baseMonthlyAmount,
        @Schema(description = "Recargo mensual por habitaciones adicionales", example = "0.00")
        BigDecimal additionalRoomsMonthlyAmount,
        @Schema(description = "Importe mensual de los actuadores seleccionados", example = "40.00")
        BigDecimal devicesMonthlyAmount,
        @Schema(description = "Importe mensual total estimado", example = "189.00")
        BigDecimal totalMonthlyAmount
) {
}
