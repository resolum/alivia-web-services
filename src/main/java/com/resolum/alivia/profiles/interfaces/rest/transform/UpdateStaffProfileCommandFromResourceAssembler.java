package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.UpdateStaffProfileCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdateStaffProfileResource;

import java.io.IOException;

public final class UpdateStaffProfileCommandFromResourceAssembler {
    private UpdateStaffProfileCommandFromResourceAssembler() {
    }

    public static UpdateStaffProfileCommand toCommandFromResource(Long profileId, UpdateStaffProfileResource resource) {
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
        return new UpdateStaffProfileCommand(
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
