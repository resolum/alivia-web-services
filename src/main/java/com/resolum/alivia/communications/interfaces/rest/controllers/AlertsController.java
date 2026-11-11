package com.resolum.alivia.communications.interfaces.rest.controllers;

import com.resolum.alivia.communications.domain.model.commands.CreateAlertCommand;
import com.resolum.alivia.communications.domain.model.queries.GetAllAlertsByUserIdQuery;
import com.resolum.alivia.communications.domain.services.AlertCommandService;
import com.resolum.alivia.communications.domain.services.AlertQueryService;
import com.resolum.alivia.communications.interfaces.rest.resources.AlertResource;
import com.resolum.alivia.communications.interfaces.rest.transform.AlertResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "Endpoints for emergency alerts")
public class AlertsController {

    private final AlertCommandService commandService;
    private final AlertQueryService queryService;

    public AlertsController(AlertCommandService commandService, AlertQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
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

    @GetMapping
    @Operation(summary = "Get user alerts history")
    public ResponseEntity<List<AlertResource>> getAll(
            Principal principal,
            @RequestParam(required = false) Long userId) {
        Long targetUserId = resolveUserId(principal, userId);
        var alerts = queryService.handle(new GetAllAlertsByUserIdQuery(targetUserId));
        var resources = alerts.stream().map(AlertResourceAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(resources);
    }

    @PostMapping
    @Operation(summary = "Create an emergency alert")
    public ResponseEntity<Long> createAlert(
            @RequestParam(required = false) Long userId,
            @RequestParam String helpRequestId,
            @RequestParam String title,
            @RequestParam String body,
            Principal principal) {
        Long targetUserId = resolveUserId(principal, userId);
        Long id = commandService.handle(new CreateAlertCommand(targetUserId, helpRequestId, title, body));
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }
}
