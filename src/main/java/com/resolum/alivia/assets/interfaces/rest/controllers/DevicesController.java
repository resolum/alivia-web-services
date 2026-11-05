package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.exceptions.DeviceNotFound;
import com.resolum.alivia.assets.domain.model.queries.GetDeviceByIdQuery;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.domain.services.DeviceQueryService;
import com.resolum.alivia.assets.interfaces.rest.resources.DeviceResource;
import com.resolum.alivia.assets.interfaces.rest.resources.RegisterDeviceResource;
import com.resolum.alivia.assets.interfaces.rest.resources.UpdateDeviceResource;
import com.resolum.alivia.assets.interfaces.rest.transform.DeviceResourceFromEntityAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.RegisterDeviceCommandFromResourceAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.UpdateDeviceCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/devices", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Dispositivos", description = "Registro y gestión del inventario de dispositivos IoT del negocio")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class DevicesController {
    private final DeviceCommandService deviceCommandService;
    private final DeviceQueryService deviceQueryService;
    private final Long defaultBusinessId;

    /**
     * @param defaultBusinessId business that owns the registered devices. Today Alivia is the only
     *                          business; it falls back to the HCM default so there is a single setting.
     *                          The household a device serves is set later, when it is assigned.
     */
    public DevicesController(DeviceCommandService deviceCommandService, DeviceQueryService deviceQueryService,
                             @Value("${alivia.assets.default-business-id:${alivia.hcm.default-business-id}}") Long defaultBusinessId) {
        this.deviceCommandService = deviceCommandService;
        this.deviceQueryService = deviceQueryService;
        this.defaultBusinessId = defaultBusinessId;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un dispositivo",
            description = "Registra un dispositivo con su identificador físico (MAC), alias y tipo. Queda en estado AVAILABLE.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dispositivo registrado"),
            @ApiResponse(responseCode = "400", description = "MAC, alias o tipo inválidos"),
            @ApiResponse(responseCode = "404", description = "El negocio no existe"),
            @ApiResponse(responseCode = "409", description = "El identificador físico ya está registrado")
    })
    public DeviceResource registerDevice(@Valid @RequestBody RegisterDeviceResource resource) {
        var deviceId = deviceCommandService.handle(
                RegisterDeviceCommandFromResourceAssembler.toCommandFromResource(resource, defaultBusinessId));
        var device = deviceQueryService.handle(new GetDeviceByIdQuery(deviceId))
                .orElseThrow(() -> new DeviceNotFound(deviceId));
        return DeviceResourceFromEntityAssembler.toResourceFromEntity(device);
    }

    @PutMapping(value = "/{deviceId}", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar un dispositivo",
            description = "Actualiza la MAC y el tipo del dispositivo. Mientras está asignado a un hogar solo puede cambiar la MAC: para cambiar el tipo hay que desasignarlo antes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispositivo actualizado"),
            @ApiResponse(responseCode = "400", description = "MAC o tipo inválidos"),
            @ApiResponse(responseCode = "404", description = "El dispositivo no existe"),
            @ApiResponse(responseCode = "409", description = "El identificador físico ya está registrado, o se pide cambiar el tipo de un dispositivo asignado")
    })
    public ResponseEntity<DeviceResource> updateDevice(@PathVariable Long deviceId, @Valid @RequestBody UpdateDeviceResource resource) {
        var updateDeviceCommand = UpdateDeviceCommandFromResourceAssembler.toCommandFromResource(deviceId, resource);
        deviceCommandService.handle(updateDeviceCommand);
        var device = deviceQueryService.handle(new GetDeviceByIdQuery(deviceId));
        return device.map(entity -> ResponseEntity.ok(DeviceResourceFromEntityAssembler.toResourceFromEntity(entity)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
