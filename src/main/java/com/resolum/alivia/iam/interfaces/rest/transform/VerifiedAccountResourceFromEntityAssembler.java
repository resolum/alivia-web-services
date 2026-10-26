package com.resolum.alivia.iam.interfaces.rest.transform;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.interfaces.rest.resources.VerifiedAccountResource;

/**
 * Assembles {@link VerifiedAccountResource} instances from {@link User} aggregates.
 */
public final class VerifiedAccountResourceFromEntityAssembler {

    private VerifiedAccountResourceFromEntityAssembler() {
    }

    /**
     * Creates a verified-account resource from a user aggregate.
     *
     * @param user the verified user
     * @return the verified-account resource
     */
    public static VerifiedAccountResource toResourceFromEntity(User user) {
        return new VerifiedAccountResource(user.getAccountId(), user.getStatus().name());
    }
}
