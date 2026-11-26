package com.resolum.alivia.communications.interfaces.rest.controllers;

import com.resolum.alivia.communications.domain.exceptions.NotificationNotFound;
import com.resolum.alivia.communications.domain.model.commands.CreateNotificationCommand;
import com.resolum.alivia.communications.domain.model.commands.MarkNotificationAsReadCommand;
import com.resolum.alivia.communications.domain.model.queries.GetAllNotificationsByUserIdQuery;
import com.resolum.alivia.communications.domain.model.queries.GetNotificationByIdQuery;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationStatus;
import com.resolum.alivia.communications.domain.model.valueobjects.NotificationType;
import com.resolum.alivia.communications.domain.services.NotificationCommandService;
import com.resolum.alivia.communications.domain.services.NotificationQueryService;
import com.resolum.alivia.communications.interfaces.rest.CallerIdentity;
import com.resolum.alivia.communications.interfaces.rest.resources.CreateNotificationResource;
import com.resolum.alivia.communications.interfaces.rest.resources.MarkNotificationsReadResource;
import com.resolum.alivia.communications.interfaces.rest.resources.NotificationResource;
import com.resolum.alivia.communications.interfaces.rest.transform.NotificationResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Endpoints for user in-app notifications")
public class NotificationsController {

    private final NotificationCommandService commandService;
    private final NotificationQueryService queryService;

    public NotificationsController(NotificationCommandService commandService,
                                   NotificationQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Get user notifications", description = "Retrieve the notifications of the authenticated user")
    public ResponseEntity<List<NotificationResource>> getAll(
            Principal principal,
            @RequestParam(required = false) String status) {
        Long targetUserId = CallerIdentity.requireUserId(principal);
        NotificationStatus notificationStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                notificationStatus = NotificationStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        var list = queryService.handle(new GetAllNotificationsByUserIdQuery(targetUserId, notificationStatus));
        var resources = list.stream().map(NotificationResourceAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notification by ID")
    public ResponseEntity<NotificationResource> getById(@PathVariable Long id, Principal principal) {
        Long userId = CallerIdentity.requireUserId(principal);
        // A notification of someone else is reported as not found, so its existence is not revealed.
        var notification = queryService.handle(new GetNotificationByIdQuery(id))
                .filter(found -> found.getRecipientId() != null && userId.equals(found.getRecipientId().userId()))
                .orElseThrow(() -> new NotificationNotFound(id));
        return ResponseEntity.ok(NotificationResourceAssembler.toResourceFromEntity(notification));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark single notification as read")
    public ResponseEntity<NotificationResource> markAsRead(
            @PathVariable Long id,
            Principal principal) {
        Long targetUserId = CallerIdentity.requireUserId(principal);
        commandService.handle(new MarkNotificationAsReadCommand(id, targetUserId));
        var notification = queryService.handle(new GetNotificationByIdQuery(id))
                .orElseThrow(() -> new NotificationNotFound(id));
        return ResponseEntity.ok(NotificationResourceAssembler.toResourceFromEntity(notification));
    }

    @PostMapping("/read")
    @Operation(summary = "Batch mark notifications as read")
    public ResponseEntity<Void> markMultipleAsRead(
            @RequestBody MarkNotificationsReadResource resource,
            Principal principal) {
        Long targetUserId = CallerIdentity.requireUserId(principal);
        if (resource != null && resource.ids() != null) {
            for (Long id : resource.ids()) {
                try {
                    commandService.handle(new MarkNotificationAsReadCommand(id, targetUserId));
                } catch (Exception ignored) {
                }
            }
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Create a notification", description = "Only an administrator creates a notification for a user")
    public ResponseEntity<NotificationResource> createNotification(
            @Valid @RequestBody CreateNotificationResource resource,
            Principal principal) {
        Long targetUserId = resource.recipientUserId() != null
                ? resource.recipientUserId() : CallerIdentity.requireUserId(principal);
        NotificationType type = NotificationType.valueOf(resource.type().toUpperCase());
        Long id = commandService.handle(new CreateNotificationCommand(
                targetUserId,
                type,
                resource.sourceId(),
                resource.title(),
                resource.body()
        ));
        var notification = queryService.handle(new GetNotificationByIdQuery(id))
                .orElseThrow(() -> new NotificationNotFound(id));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(NotificationResourceAssembler.toResourceFromEntity(notification));
    }
}
