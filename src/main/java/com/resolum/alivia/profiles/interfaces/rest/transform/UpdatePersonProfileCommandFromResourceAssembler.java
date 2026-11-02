package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.UpdatePersonProfileCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdatePersonProfileResource;

public final class UpdatePersonProfileCommandFromResourceAssembler {
    private UpdatePersonProfileCommandFromResourceAssembler() {
    }

    public static UpdatePersonProfileCommand toCommandFromResource(Long profileId, UpdatePersonProfileResource resource) {
        return new UpdatePersonProfileCommand(
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
