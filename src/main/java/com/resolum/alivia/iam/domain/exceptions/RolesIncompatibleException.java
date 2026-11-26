package com.resolum.alivia.iam.domain.exceptions;

import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.services.RoleParser;

public class RolesIncompatibleException extends RuntimeException {
    public RolesIncompatibleException(AccessRole requested, AccessRole held) {
        super("Role " + RoleParser.toApiName(requested) + " cannot be combined with " + RoleParser.toApiName(held) + ".");
    }
}
