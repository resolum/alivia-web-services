package com.resolum.alivia.communications.interfaces.rest.resources;

import java.util.List;

public record MarkNotificationsReadResource(
        List<Long> ids
) {
}
