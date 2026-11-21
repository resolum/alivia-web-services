package com.resolum.alivia.iam.interfaces.rest.transform;

import com.resolum.alivia.iam.domain.services.RoleParser;
import com.resolum.alivia.iam.domain.model.valueobjects.AuthSession;
import com.resolum.alivia.iam.interfaces.rest.resources.AuthenticatedUserResource;


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
        var primaryRole = user.getAccessRole();
        return new AuthenticatedUserResource(
                session.accessToken(),
                session.refreshToken(),
                "Bearer",
                session.expiresIn(),
                new AuthenticatedUserResource.AccountResource(
                        user.getAccountId(), user.getEmail().email(),
                        primaryRole == null ? null : primaryRole.name(),
                        user.getRoles().stream().map(RoleParser::toApiName).sorted().toList(),
                        user.getStatus().name(), user.getProfileId()),
                user.getRoles().stream().map(role -> "ROLE_" + role.name()).sorted().toList());
    }
}
