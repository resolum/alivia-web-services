package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import com.resolum.alivia.subscriptions.domain.model.valueobjects.PlanCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "BorradorConfiguracion",
        description = "Estado persistido del paso de configuración previo a la evaluación y al pago")
public record ConfigurationDraftResource(
        @Schema(description = "UUID del borrador", example = "5cf5d80a-4f88-44ae-9ccb-642c68c065ab")
        String id,
        @Schema(description = "Plan asociado", example = "CUSTOM")
        PlanCode planCode,
        @Schema(description = "Periodicidad elegida", example = "MONTHLY",
                allowableValues = {"MONTHLY", "ANNUAL"})
        String billingCycle,
        @Schema(description = "Distribución actual de habitaciones y dispositivos")
        List<ConfiguredRoomResource> rooms,
        @Schema(description = "Cotización calculada; puede ser nula si la configuración está incompleta",
                nullable = true)
        PlanQuoteResource quote,
        @Schema(description = "Fecha y hora UTC de la última actualización", example = "2026-10-24T19:30:00Z")
        Instant updatedAt
) {
    @Schema(name = "HabitacionConfigurada",
            description = "Habitación almacenada dentro del borrador de configuración")
    public record ConfiguredRoomResource(
            @Schema(description = "Identificador estable", example = "main-bedroom")
            String key,
            @Schema(description = "Nombre visible", example = "Dormitorio principal")
            String name,
            @Schema(description = "Actuadores de iluminación", example = "2", minimum = "0")
            int lights,
            @Schema(description = "Actuadores de puerta", example = "1", minimum = "0")
            int doors,
            @Schema(description = "Actuadores de ventana", example = "0", minimum = "0")
            int windows,
            @Schema(description = "Actuadores de cortina", example = "0", minimum = "0")
            int curtains,
            @Schema(description = "Micrófonos asistenciales incluidos", example = "1")
            int microphones
    ) {
    }
}
