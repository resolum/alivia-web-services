package com.resolum.alivia.assets.interfaces.rest.controllers;

import com.resolum.alivia.assets.domain.model.queries.GetDevIceByIdQuery;
import com.resolum.alivia.assets.domain.services.DeviceCommandService;
import com.resolum.alivia.assets.domain.services.DeviceQueryService;
import com.resolum.alivia.assets.interfaces.rest.resources.DeviceResource;
import com.resolum.alivia.assets.interfaces.rest.resources.UpdateDeviceResource;
import com.resolum.alivia.assets.interfaces.rest.transform.DeviceResourceFromEntityAssembler;
import com.resolum.alivia.assets.interfaces.rest.transform.UpdateDeviceCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "api/v1/devices",produces = APPLICATION_JSON_VALUE)
@Tag(name = "devices",description = "")
public class DevicesController {
    private final DeviceCommandService deviceCommandService;
    private final DeviceQueryService deviceQueryService;

    public DevicesController(DeviceCommandService deviceCommandService, DeviceQueryService deviceQueryService) {
        this.deviceCommandService = deviceCommandService;
        this.deviceQueryService = deviceQueryService;
    }
    @PutMapping(value = "/{deviceId}")
    @Operation(description = "")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode ="",description = ""),
                    @ApiResponse(responseCode = "",description = "" )

            }
    )
    ResponseEntity<DeviceResource> updateDevice(@PathVariable  Long deviceId, @Valid @RequestBody UpdateDeviceResource resource){
     var updateDeviceCommand = UpdateDeviceCommandFromResourceAssembler.toCommandFromResource(deviceId,resource);
     deviceCommandService.handle(updateDeviceCommand);
     var getDeviceByIdQuery = deviceQueryService.handle(new GetDevIceByIdQuery(deviceId));
     if (getDeviceByIdQuery.isEmpty()){return ResponseEntity.notFound().build();}
     var deviceEntity= getDeviceByIdQuery.get();
     var deviceResource= DeviceResourceFromEntityAssembler.toResourceFromEntity(deviceEntity);
     return ResponseEntity.ok(deviceResource);

    }
}
