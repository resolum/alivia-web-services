package com.resolum.alivia.iam.interfaces.rest.transform;

import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
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
        if (resource == null) {
            return null;
        }
        AccessRole role = parseRole(resource.role());
        return new SignUpCommand(
                resource.email(),
                resource.password(),
                resource.fullName(),
                resource.phoneNumber(),
                Boolean.TRUE.equals(resource.acceptedTerms()),
                role,
                resource.businessId(),
                resource.familiarId()
        );
    }

    private static AccessRole parseRole(String roleStr) {
        try {
            return com.resolum.alivia.iam.domain.services.RoleParser.parse(roleStr);
        } catch (IllegalArgumentException e) {
            throw new SignUpValidationException("role", "invalid_role");
        }
    }
}
