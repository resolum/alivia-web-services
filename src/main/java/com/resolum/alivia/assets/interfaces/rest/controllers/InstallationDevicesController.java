package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.interfaces.rest.resources.AssignDeviceResource;
import com.resolum.alivia.assets.interfaces.rest.resources.DeviceAssignmentResource;
import com.resolum.alivia.assets.interfaces.rest.transform.AssignDeviceCommandFromResourceAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.DeviceAssignmentResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/installations/{installationId}/devices", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Dispositivos de una instalación", description = "Asignación de dispositivos del inventario al hogar de una instalación")
public class InstallationDevicesController {
    private static final String ADMINISTRATOR_ROLE = "ROLE_ADMINISTRATOR";

    private final DeviceAssignmentCommandService assignmentCommandService;

    public InstallationDevicesController(DeviceAssignmentCommandService assignmentCommandService) {
        this.assignmentCommandService = assignmentCommandService;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Asignar un dispositivo a una instalación",
            description = "Coloca un dispositivo disponible en una habitación del hogar de la instalación, con su función. "
                    + "Puede hacerlo quien administra el hogar o el administrador del negocio. El dispositivo queda IN_USE.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dispositivo asignado"),
            @ApiResponse(responseCode = "400", description = "Habitación, función o dispositivo inválidos"),
            @ApiResponse(responseCode = "403", description = "El usuario no administra el hogar de la instalación"),
            @ApiResponse(responseCode = "404", description = "La instalación o el dispositivo no existen"),
            @ApiResponse(responseCode = "409", description = "El dispositivo ya está asignado o no está disponible, o la instalación no está activa"),
            @ApiResponse(responseCode = "422", description = "El dispositivo no es compatible con el plan del hogar")
    })
    public DeviceAssignmentResource assignDevice(@PathVariable Long installationId,
                                                 @Valid @RequestBody AssignDeviceResource resource,
                                                 @Parameter(hidden = true) Authentication authentication) {
        var administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMINISTRATOR_ROLE.equals(authority.getAuthority()));
        var command = AssignDeviceCommandFromResourceAssembler.toCommandFromResource(
                installationId, Long.valueOf(authentication.getName()), administrator, resource);
        return DeviceAssignmentResourceFromEntityAssembler.toResourceFromEntity(assignmentCommandService.handle(command));
    }
}
