package com.resolum.alivia.operations.interfaces.rest.resources;

import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDeviceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Device the technician registers at the home. */
public record RegisterInstalledDeviceResource(
        @Schema(description = "Nombre descriptivo de la ubicación del dispositivo", example = "Micrófono Cabecera")
        @NotBlank @Size(max = 60) String alias,
        @Schema(description = "Dirección MAC en pares hexadecimales", example = "00:1A:2B:3C:4D:5E")
        @NotBlank String macAddress,
        @Schema(description = "Tipo de dispositivo", example = "MICROPHONE")
        @NotNull InstalledDeviceType type) {
}
