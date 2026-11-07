package com.resolum.alivia.nursing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Datos para programar la administración de un medicamento")
public record CreateMedicationScheduleResource(
        @NotBlank @Schema(description = "Nombre del medicamento", example = "Losartan") String medication,
        @Schema(description = "Dosis y concentración", example = "50 mg") String dose,
        @NotEmpty @Schema(description = "Horarios de administración en formato HH:mm", example = "[\"08:00\", \"20:00\"]") List<String> times,
        @Schema(description = "Frecuencia de toma", example = "daily") String frequency,
        @Schema(description = "Fecha de inicio (ISO YYYY-MM-DD)", example = "2026-11-07") String startDate,
        @Schema(description = "Fecha de fin opcional (ISO YYYY-MM-DD)", example = "2026-12-07") String endDate,
        @Schema(description = "Identificador de la membresía del cuidador asignado", example = "1") Long caregiverId,
        @Schema(description = "Identificador del perfil de la persona asistida", example = "8") Long assistedPersonId,
        @Schema(description = "Referencia de la receta o indicación", example = "Receta Dr. Paredes") String indicationRef
) {}
