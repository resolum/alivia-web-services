package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.model.commands.PlaceEdgeInInstallationCommand;
import com.resolum.alivia.assets.domain.model.commands.RemoveEdgeFromInstallationCommand;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryCommandService;
import com.resolum.alivia.assets.interfaces.rest.resources.InstallationEdgesResource;
import com.resolum.alivia.assets.interfaces.rest.transform.InstallationEdgesResourceFromViewAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/installations/{installationId}/edges/{edgeId}", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Edges de una instalación", description = "Asignación de los edge services (micrófono y actuadores) al hogar de una instalación")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class InstallationEdgesController {
    private final HouseholdDeviceRegistryCommandService registryCommandService;

    public InstallationEdgesController(HouseholdDeviceRegistryCommandService registryCommandService) {
        this.registryCommandService = registryCommandService;
    }

    @PutMapping
    @Operation(summary = "Asignar un edge a una instalación",
            description = "Coloca un edge disponible en el lugar de su tipo (micrófono o actuadores) y lo deja IN_USE. "
                    + "Si ya había otro edge de ese tipo, vuelve al inventario y pierde su credencial. Repetir la petición "
                    + "con el edge que ya ocupa el lugar no cambia nada. No cambia la tabla de dispositivos del hogar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Edge asignado; devuelve los edge de la instalación"),
            @ApiResponse(responseCode = "404", description = "La instalación o el edge no existen"),
            @ApiResponse(responseCode = "409", description = "El edge no está disponible (ya asignado, en mantenimiento, averiado o retirado), o la instalación no está activa")
    })
    public InstallationEdgesResource placeEdge(@PathVariable Long installationId, @PathVariable Long edgeId) {
        return InstallationEdgesResourceFromViewAssembler.toResourceFromView(
                registryCommandService.handle(new PlaceEdgeInInstallationCommand(installationId, edgeId)));
    }

    @DeleteMapping
    @Operation(summary = "Quitar un edge de una instalación",
            description = "Devuelve el edge al inventario (disponible, salvo que estuviera en mantenimiento o averiado) y revoca su credencial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Edge quitado; devuelve los edge que quedan en la instalación"),
            @ApiResponse(responseCode = "404", description = "La instalación no existe"),
            @ApiResponse(responseCode = "409", description = "El edge no está asignado a esa instalación")
    })
    public InstallationEdgesResource removeEdge(@PathVariable Long installationId, @PathVariable Long edgeId) {
        return InstallationEdgesResourceFromViewAssembler.toResourceFromView(
                registryCommandService.handle(new RemoveEdgeFromInstallationCommand(installationId, edgeId)));
    }
}
