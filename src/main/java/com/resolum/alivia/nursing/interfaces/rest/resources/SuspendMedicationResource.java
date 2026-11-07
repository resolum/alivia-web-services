package com.resolum.alivia.nursing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos para suspender un medicamento")
public record SuspendMedicationResource(
        @Schema(description = "Fecha de suspensión (ISO)", example = "2026-11-08") String from,
        @Schema(description = "Origen o motivo médico de la suspensión", example = "Dr. Salas - suspensión por mejoría") String source
) {}
