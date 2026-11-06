package com.resolum.alivia.assets.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

public record EdgeInventoryItemResource(Long id, Long businessId, String macAddress, String edgeType, String status,
                                        @Schema(description = "Instalación donde está asignado; nulo si no está asignado")
                                        Long installationId) {
}
