package com.resolum.alivia.iam.interfaces.rest.transform;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.interfaces.rest.resources.RegisteredAccountResource;

/**
 * Assembles {@link RegisteredAccountResource} instances from {@link User} aggregates.
 */
public final class  RegisteredAccountResourceFromEntityAssembler {

    private RegisteredAccountResourceFromEntityAssembler() {
    }

    /**
     * Creates a registered-account resource from a user aggregate.
     *
     * @param user the registered user
     * @return the registered-account resource
     */
    public static RegisteredAccountResource toResourceFromEntity(User user) {
        return new RegisteredAccountResource(
                user.getId(),
                maskEmail(user.getEmail().email()),
                user.getStatus().name());
    }

    private static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
