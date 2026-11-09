package com.resolum.alivia.subscriptions.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record ChangeSubscriptionStatusResource(
        @NotBlank String status
) {
}
