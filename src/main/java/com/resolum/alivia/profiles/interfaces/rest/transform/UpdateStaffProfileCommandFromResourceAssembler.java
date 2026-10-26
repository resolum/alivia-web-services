package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.UpdateStaffProfileCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdateStaffProfileResource;

public final class UpdateStaffProfileCommandFromResourceAssembler {
    private UpdateStaffProfileCommandFromResourceAssembler() {
    }

    public static UpdateStaffProfileCommand toCommandFromResource(Long profileId, UpdateStaffProfileResource resource) {
        return new UpdateStaffProfileCommand(
                profileId,
                resource.firstName(),
                resource.lastName(),
                resource.birthDate(),
                resource.phoneNumber(),
                resource.photoUrl(),
                resource.photoPublicId()
        );
    }
}
