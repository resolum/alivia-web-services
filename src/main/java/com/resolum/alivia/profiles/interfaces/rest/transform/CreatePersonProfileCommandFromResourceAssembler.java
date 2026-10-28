package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.CreatePersonProfileCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.CreatePersonProfileResource;

import java.io.IOException;

public final class CreatePersonProfileCommandFromResourceAssembler {
    private CreatePersonProfileCommandFromResourceAssembler() {
    }

    public static CreatePersonProfileCommand toCommandFromResource(CreatePersonProfileResource resource) {
        if (resource == null) return null;
        byte[] photoData = null;
        String photoFileName = null;
        if (resource.photo() != null && !resource.photo().isEmpty()) {
            try {
                photoData = resource.photo().getBytes();
                photoFileName = resource.photo().getOriginalFilename();
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid photo file", e);
            }
        }
        return new CreatePersonProfileCommand(
                resource.emailAddress(),
                photoData,
                photoFileName,
                resource.phoneNumber(),
                resource.firstName(),
                resource.lastName(),
                resource.dni(),
                resource.birthDate()
        );
    }
}
