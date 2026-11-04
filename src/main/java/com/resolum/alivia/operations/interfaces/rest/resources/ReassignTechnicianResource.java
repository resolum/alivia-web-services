package com.resolum.alivia.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why the responsible technician can no longer attend, kept in the order's history. */
public record ReassignTechnicianResource(@NotBlank @Size(max = 300) String reason) {
}
