package com.resolum.alivia.nursing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Datos para actualizar la indicación médica")
public record UpdateMedicationScheduleResource(
        @Schema(description = "Dosis", example = "100 mg") String dose,
        @NotEmpty @Schema(description = "Horarios de administración en formato HH:mm") List<String> times,
        @Schema(description = "Frecuencia", example = "daily") String frequency,
        @Schema(description = "Fecha desde la que aplica el cambio (ISO)", example = "2026-11-08") String effectiveFrom,
        @Schema(description = "Referencia de la nueva indicación", example = "Ajuste por Dr. Salas") String indicationRef
) {}
