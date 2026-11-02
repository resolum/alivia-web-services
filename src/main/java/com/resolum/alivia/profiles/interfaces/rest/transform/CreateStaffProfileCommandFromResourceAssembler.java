package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.CreateStaffProfileCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.CreateStaffProfileResource;

public final class CreateStaffProfileCommandFromResourceAssembler {
    private CreateStaffProfileCommandFromResourceAssembler() {
    }

    public static CreateStaffProfileCommand toCommandFromResource(CreateStaffProfileResource resource) {
        return new CreateStaffProfileCommand(
                resource.emailAddress(),
                resource.photoUrl(),
                resource.photoPublicId(),
                resource.phoneNumber(),
                resource.firstName(),
                resource.lastName(),
                resource.dni(),
                resource.birthDate()
        );
    }
}
