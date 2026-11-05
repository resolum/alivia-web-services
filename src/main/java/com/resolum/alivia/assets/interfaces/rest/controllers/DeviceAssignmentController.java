package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.interfaces.rest.resources.UnassignDeviceResource;
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
@RequestMapping(value = "/api/v1/devices/{deviceId}/assignment", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Asignación de un dispositivo", description = "Retiro de un dispositivo del hogar al que está asignado")
public class DeviceAssignmentController {
    private static final String ADMINISTRATOR_ROLE = "ROLE_ADMINISTRATOR";

    private final DeviceAssignmentCommandService assignmentCommandService;

    public DeviceAssignmentController(DeviceAssignmentCommandService assignmentCommandService) {
        this.assignmentCommandService = assignmentCommandService;
    }

    @DeleteMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desasignar un dispositivo",
            description = "Cierra la asignación vigente del dispositivo con el motivo indicado y lo deja disponible. "
                    + "La asignación se conserva como historial; no se borra ninguna fila. "
                    + "Puede hacerlo quien administra el hogar o el administrador del negocio.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Dispositivo desasignado"),
            @ApiResponse(responseCode = "400", description = "Motivo ausente o demasiado largo"),
            @ApiResponse(responseCode = "403", description = "El usuario no administra el hogar del dispositivo"),
            @ApiResponse(responseCode = "404", description = "El dispositivo no existe"),
            @ApiResponse(responseCode = "409", description = "El dispositivo no está asignado")
    })
    public void unassignDevice(@PathVariable Long deviceId, @Valid @RequestBody UnassignDeviceResource resource,
                               @Parameter(hidden = true) Authentication authentication) {
        var administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMINISTRATOR_ROLE.equals(authority.getAuthority()));
        assignmentCommandService.handle(new UnassignDeviceCommand(
                deviceId, resource.reason(), Long.valueOf(authentication.getName()), administrator));
    }
}
