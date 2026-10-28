package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.CreateStaffProfileCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.CreateStaffProfileResource;

import java.io.IOException;

public final class CreateStaffProfileCommandFromResourceAssembler {
    private CreateStaffProfileCommandFromResourceAssembler() {
    }

    public static CreateStaffProfileCommand toCommandFromResource(CreateStaffProfileResource resource) {
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
        return new CreateStaffProfileCommand(
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
