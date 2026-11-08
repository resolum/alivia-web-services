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
import com.resolum.alivia.communications.interfaces.rest.resources.CreateNotificationResource;
import com.resolum.alivia.communications.interfaces.rest.resources.MarkNotificationsReadResource;
import com.resolum.alivia.communications.interfaces.rest.resources.NotificationResource;
import com.resolum.alivia.communications.interfaces.rest.transform.NotificationResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
        return 1L; // default fallback for unauthenticated / testing environments
    }

    @GetMapping
    @Operation(summary = "Get user notifications", description = "Retrieve notifications for the current or specified user")
    public ResponseEntity<List<NotificationResource>> getAll(
            Principal principal,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        Long targetUserId = resolveUserId(principal, userId);
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
    public ResponseEntity<NotificationResource> getById(@PathVariable Long id) {
        var notification = queryService.handle(new GetNotificationByIdQuery(id))
                .orElseThrow(() -> new NotificationNotFound(id));
        return ResponseEntity.ok(NotificationResourceAssembler.toResourceFromEntity(notification));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark single notification as read")
    public ResponseEntity<NotificationResource> markAsRead(
            @PathVariable Long id,
            Principal principal,
            @RequestParam(required = false) Long userId) {
        Long targetUserId = resolveUserId(principal, userId);
        commandService.handle(new MarkNotificationAsReadCommand(id, targetUserId));
        var notification = queryService.handle(new GetNotificationByIdQuery(id))
                .orElseThrow(() -> new NotificationNotFound(id));
        return ResponseEntity.ok(NotificationResourceAssembler.toResourceFromEntity(notification));
    }

    @PostMapping("/read")
    @Operation(summary = "Batch mark notifications as read")
    public ResponseEntity<Void> markMultipleAsRead(
            @RequestBody MarkNotificationsReadResource resource,
            Principal principal,
            @RequestParam(required = false) Long userId) {
        Long targetUserId = resolveUserId(principal, userId);
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
    @Operation(summary = "Create a notification")
    public ResponseEntity<NotificationResource> createNotification(
            @Valid @RequestBody CreateNotificationResource resource,
            Principal principal) {
        Long targetUserId = resolveUserId(principal, resource.recipientUserId());
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
