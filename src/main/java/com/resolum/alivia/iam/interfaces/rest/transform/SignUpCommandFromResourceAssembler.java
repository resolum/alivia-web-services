package com.resolum.alivia.iam.interfaces.rest.transform;

import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.interfaces.rest.resources.SignUpResource;

/**
 * Assembles {@link SignUpCommand} instances from REST resources.
 */
public final class SignUpCommandFromResourceAssembler {

    private SignUpCommandFromResourceAssembler() {
    }

    /**
     * Creates a sign-up command from a request resource.
     *
     * @param resource the sign-up request resource
     * @return the sign-up command
     */
    public static SignUpCommand toCommandFromResource(SignUpResource resource) {
        return new SignUpCommand(
                resource.email(),
                resource.password(),
                resource.fullName(),
                resource.phoneNumber(),
                Boolean.TRUE.equals(resource.acceptedTerms()));
    }
}
