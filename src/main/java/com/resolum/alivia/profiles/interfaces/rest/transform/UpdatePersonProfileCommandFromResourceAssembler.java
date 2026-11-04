package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.UpdatePersonProfileCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdatePersonProfileResource;

import java.io.IOException;

public final class UpdatePersonProfileCommandFromResourceAssembler {
    private UpdatePersonProfileCommandFromResourceAssembler() {
    }

    public static UpdatePersonProfileCommand toCommandFromResource(Long profileId, UpdatePersonProfileResource resource) {
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
        return new UpdatePersonProfileCommand(
                profileId,
                resource.firstName(),
                resource.lastName(),
                resource.birthDate(),
                resource.phoneNumber(),
                photoData,
                photoFileName
        );
    }
}
