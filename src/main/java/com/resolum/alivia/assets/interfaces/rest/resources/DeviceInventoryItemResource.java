package com.resolum.alivia.assets.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record DeviceInventoryItemResource(Long id, Long businessId, String macAddress, String alias, String deviceType,
                                          String status,
                                          @Schema(description = "Instalación donde está asignado; nulo si no está asignado")
                                          Long installationId) {
}
