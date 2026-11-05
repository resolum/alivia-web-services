package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.model.commands.UnassignDeviceCommand;
import com.resolum.alivia.assets.domain.services.DeviceAssignmentCommandService;
import com.resolum.alivia.assets.interfaces.rest.transform.CallerAuthorityFromAuthenticationAssembler;
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
    private final DeviceAssignmentCommandService assignmentCommandService;

    public DeviceAssignmentController(DeviceAssignmentCommandService assignmentCommandService) {
        this.assignmentCommandService = assignmentCommandService;
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desasignar un dispositivo",
            description = "Cierra la asignación vigente del dispositivo con el motivo indicado y lo deja disponible. "
                    + "El motivo va en el cuerpo JSON {\"reason\": \"...\"} o, para los clientes que descartan el cuerpo "
                    + "de un DELETE, en el parámetro de consulta `reason`; si llegan ambos, vale el del cuerpo. "
                    + "La asignación se conserva como historial; no se borra ninguna fila. "
                    + "Pueden hacerlo quien administra el hogar y el administrador del negocio.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Dispositivo desasignado"),
            @ApiResponse(responseCode = "400", description = "Motivo ausente o demasiado largo"),
            @ApiResponse(responseCode = "403", description = "El usuario no administra el hogar del dispositivo"),
            @ApiResponse(responseCode = "404", description = "El dispositivo no existe"),
            @ApiResponse(responseCode = "409", description = "El dispositivo no está asignado")
    })
    public void unassignDevice(@PathVariable Long deviceId,
                               @Valid @RequestBody(required = false) UnassignDeviceResource body,
                               @RequestParam(name = "reason", required = false) String reasonParameter,
                               @Parameter(hidden = true) Authentication authentication) {
        var reason = body != null ? body.reason() : reasonParameter;
        assignmentCommandService.handle(new UnassignDeviceCommand(
                deviceId, reason, Long.valueOf(authentication.getName()),
                CallerAuthorityFromAuthenticationAssembler.toCallerAuthority(authentication)));
    }
}
