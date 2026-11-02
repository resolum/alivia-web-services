package com.resolum.alivia.iam.interfaces.rest.transform;

import com.resolum.alivia.iam.domain.model.commands.VerifyEmailCommand;
import com.resolum.alivia.iam.interfaces.rest.resources.VerifyEmailResource;

/**
 * Assembles {@link VerifyEmailCommand} instances from REST resources.
 */
public final class VerifyEmailCommandFromResourceAssembler {

    private VerifyEmailCommandFromResourceAssembler() {
    }

    /**
     * Creates a verify-email command from a request resource.
     *
     * @param resource the verify-email request resource
     * @return the verify-email command
     */
    public static VerifyEmailCommand toCommandFromResource(VerifyEmailResource resource) {
        return new VerifyEmailCommand(resource.token());
    }
}
