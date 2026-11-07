package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.CreateHouseholdCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.CreateHouseholdResource;

/**
 * Assembler to map {@link CreateHouseholdResource} to {@link CreateHouseholdCommand}.
 */
public final class CreateHouseholdCommandFromResourceAssembler {

    private CreateHouseholdCommandFromResourceAssembler() {
    }

    public static CreateHouseholdCommand toCommandFromResource(CreateHouseholdResource resource, Long responsibleUserId) {
        if (resource == null) {
            return null;
        }
        return new CreateHouseholdCommand(
                resource.alias(),
                resource.phoneNumber(),
                resource.contactEmail(),
                resource.address(),
                resource.district(),
                resource.city(),
                resource.formattedAddress(),
                resource.latitude(),
                resource.longitude(),
                resource.placeId(),
                responsibleUserId != null ? responsibleUserId : resource.responsibleAccountId()
        );
    }
}
