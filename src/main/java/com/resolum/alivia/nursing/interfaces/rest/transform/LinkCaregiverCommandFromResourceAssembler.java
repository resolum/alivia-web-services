package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.resolum.alivia.nursing.domain.model.commands.LinkCaregiverCommand;
import com.resolum.alivia.nursing.interfaces.rest.resources.LinkCaregiverResource;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Assembles {@link LinkCaregiverCommand} instances from request bodies.
 */
public final class LinkCaregiverCommandFromResourceAssembler {

    private LinkCaregiverCommandFromResourceAssembler() {
    }

    /**
     * Converts a request body to its command.
     *
     * @param requesterUserId the authenticated user
     * @param householdId     the household from the path
     * @param resource        the request body
     * @return the command
     */
    public static LinkCaregiverCommand toCommandFromResource(UserId requesterUserId, Long householdId,
                                                             LinkCaregiverResource resource) {
        return new LinkCaregiverCommand(requesterUserId, householdId,
                new PersonProfileId(resource.assistedPersonId()), new UserId(resource.caregiverUserId()));
    }
}
