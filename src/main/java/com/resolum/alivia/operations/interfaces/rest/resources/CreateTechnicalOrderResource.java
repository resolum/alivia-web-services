package com.resolum.alivia.operations.interfaces.rest.resources;

import com.resolum.alivia.operations.domain.model.valueobjects.ServiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Order in a block returned by the availability. The contact can be left out for an installation: it is
 * copied from the evaluation of the same request.
 */
public record CreateTechnicalOrderResource(
        @NotBlank @Size(max = 36) String draftId,
        @NotNull ServiceType serviceType,
        @NotBlank String slotId,
        @Size(max = 100) String contactName,
        @Size(max = 20) String contactPhone,
        @Size(max = 300) String directions) {
}
