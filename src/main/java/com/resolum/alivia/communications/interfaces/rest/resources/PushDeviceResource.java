package com.resolum.alivia.communications.interfaces.rest.resources;

public record PushDeviceResource(
        Long id,
        Long userId,
        String token,
        String platform,
        String createdAt
) {
}
