package com.resolum.alivia.communications.interfaces.rest.controllers;

import com.resolum.alivia.communications.domain.model.commands.RegisterPushDeviceCommand;
import com.resolum.alivia.communications.domain.model.commands.UnregisterPushDeviceCommand;
import com.resolum.alivia.communications.domain.model.valueobjects.PushPlatform;
import com.resolum.alivia.communications.domain.services.PushDeviceCommandService;
import com.resolum.alivia.communications.interfaces.rest.resources.RegisterPushDeviceResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/push-devices")
@Tag(name = "Push Devices", description = "Endpoints for mobile push notification device registration")
public class PushDevicesController {

    private final PushDeviceCommandService commandService;

    public PushDevicesController(PushDeviceCommandService commandService) {
        this.commandService = commandService;
    }

    private Long resolveUserId(Principal principal, Long requestedUserId) {
        if (requestedUserId != null) {
            return requestedUserId;
        }
        if (principal != null) {
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException ignored) {
            }
        }
        return 1L;
    }

    @PostMapping
    @Operation(summary = "Register or reassign a mobile push token")
    public ResponseEntity<Long> register(
            @Valid @RequestBody RegisterPushDeviceResource resource,
            Principal principal) {
        Long targetUserId = resolveUserId(principal, resource.userId());
        PushPlatform platform = PushPlatform.ANDROID;
        if (resource.platform() != null && !resource.platform().isBlank()) {
            try {
                platform = PushPlatform.valueOf(resource.platform().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        Long id = commandService.handle(new RegisterPushDeviceCommand(targetUserId, resource.token(), platform));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @DeleteMapping("/{token}")
    @Operation(summary = "Unregister a push device token")
    public ResponseEntity<Void> unregister(
            @PathVariable String token,
            @RequestParam(required = false) Long userId,
            Principal principal) {
        Long targetUserId = resolveUserId(principal, userId);
        commandService.handle(new UnregisterPushDeviceCommand(targetUserId, token));
        return ResponseEntity.noContent().build();
    }
}
