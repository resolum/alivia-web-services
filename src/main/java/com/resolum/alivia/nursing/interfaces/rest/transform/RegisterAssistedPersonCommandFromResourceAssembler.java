package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.resolum.alivia.nursing.domain.model.commands.RegisterAssistedPersonCommand;
import com.resolum.alivia.nursing.interfaces.rest.resources.RegisterAssistedPersonResource;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Assembles {@link RegisterAssistedPersonCommand} instances from request bodies.
 */
public final class RegisterAssistedPersonCommandFromResourceAssembler {

    private RegisterAssistedPersonCommandFromResourceAssembler() {
    }

    /**
     * Converts a request body to its command.
     *
     * @param requesterUserId the authenticated user
     * @param householdId     the household from the path
     * @param resource        the request body
     * @return the command
     */
    public static RegisterAssistedPersonCommand toCommandFromResource(UserId requesterUserId, Long householdId,
                                                                      RegisterAssistedPersonResource resource) {
        return new RegisterAssistedPersonCommand(requesterUserId, householdId, resource.email(),
                resource.password(), resource.firstName(), resource.lastName(), resource.phoneNumber(),
                resource.dni(), resource.birthDate());
    }
}
