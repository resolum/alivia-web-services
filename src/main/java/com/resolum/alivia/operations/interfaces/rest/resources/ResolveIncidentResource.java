package com.resolum.alivia.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** How the technician closes a maintenance visit from the app: what they found and the work that left the device operating. */
public record ResolveIncidentResource(@NotBlank @Size(max = 500) String diagnosis,
                                      @NotBlank @Size(max = 500) String workPerformed) {
}
