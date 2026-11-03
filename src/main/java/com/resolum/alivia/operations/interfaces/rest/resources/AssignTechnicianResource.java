package com.resolum.alivia.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignTechnicianResource(@NotNull Long technicianId, @Size(max = 300) String note) {
}
