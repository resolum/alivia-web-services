package com.resolum.alivia.communications.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateNotificationResource(
        Long recipientUserId,
        @NotNull String type,
        @NotBlank String sourceId,
        @NotBlank String title,
        @NotBlank String body
) {
}
