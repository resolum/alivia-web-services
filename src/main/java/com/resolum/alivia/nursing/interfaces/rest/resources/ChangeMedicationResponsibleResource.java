package com.resolum.alivia.nursing.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos para cambiar el cuidador responsable")
public record ChangeMedicationResponsibleResource(
        @Schema(description = "Identificador de la membresía del cuidador", example = "2") Long caregiverId
) {}
