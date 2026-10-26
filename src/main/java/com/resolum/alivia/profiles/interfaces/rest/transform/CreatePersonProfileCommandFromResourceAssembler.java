package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.CreatePersonProfileCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.CreatePersonProfileResource;

public final class CreatePersonProfileCommandFromResourceAssembler {
    private CreatePersonProfileCommandFromResourceAssembler() {
    }

    public static CreatePersonProfileCommand toCommandFromResource(CreatePersonProfileResource resource) {
        return new CreatePersonProfileCommand(
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
