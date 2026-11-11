package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.resolum.alivia.profiles.domain.model.commands.UpdateHouseholdCommand;
import com.resolum.alivia.profiles.interfaces.rest.resources.UpdateHouseholdResource;

/**
 * Assembler to map {@link UpdateHouseholdResource} to {@link UpdateHouseholdCommand}.
 */
public final class UpdateHouseholdCommandFromResourceAssembler {

    private UpdateHouseholdCommandFromResourceAssembler() {
    }

    public static UpdateHouseholdCommand toCommandFromResource(Long householdId, UpdateHouseholdResource resource) {
        if (resource == null) {
            return null;
        }
        return new UpdateHouseholdCommand(
                householdId,
                resource.alias(),
                resource.phoneNumber(),
                resource.contactEmail()
        );
    }
}
