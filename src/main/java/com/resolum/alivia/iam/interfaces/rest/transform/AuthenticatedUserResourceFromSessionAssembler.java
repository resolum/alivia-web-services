package com.resolum.alivia.iam.interfaces.rest.transform;

import com.resolum.alivia.iam.domain.model.valueobjects.AuthSession;
import com.resolum.alivia.iam.interfaces.rest.resources.AuthenticatedUserResource;

import java.util.List;

/**
 * Assembles {@link AuthenticatedUserResource} instances from {@link AuthSession} values.
 */
public final class AuthenticatedUserResourceFromSessionAssembler {

    private AuthenticatedUserResourceFromSessionAssembler() {
    }

    /**
     * Creates an authenticated-user resource from an issued session.
     *
     * @param session the issued session
     * @return the authenticated-user resource
     */
    public static AuthenticatedUserResource toResourceFromSession(AuthSession session) {
        var user = session.user();
        return new AuthenticatedUserResource(
                session.accessToken(),
                session.refreshToken(),
                "Bearer",
                session.expiresIn(),
                new AuthenticatedUserResource.AccountResource(
                        user.getAccountId(), user.getEmail().email(),
                        user.getAccessRole().name(), user.getStatus().name()),
                List.of("ROLE_" + user.getAccessRole().name()));
    }
}
