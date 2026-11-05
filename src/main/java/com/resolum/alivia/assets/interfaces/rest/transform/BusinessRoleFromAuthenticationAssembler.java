package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.valueobjects.BusinessRole;
import org.springframework.security.core.Authentication;

public class BusinessRoleFromAuthenticationAssembler {
    private static final String ADMINISTRATOR_ROLE = "ROLE_ADMINISTRATOR";
    private static final String TECHNICIAN_ROLE = "ROLE_TECHNICIAN";

    /** What the authenticated account is to the business; anyone else is acting as a household member. */
    public static BusinessRole toBusinessRole(Authentication authentication) {
        var authorities = authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).toList();
        if (authorities.contains(ADMINISTRATOR_ROLE)) {
            return BusinessRole.ADMINISTRATOR;
        }
        return authorities.contains(TECHNICIAN_ROLE) ? BusinessRole.TECHNICIAN : BusinessRole.NONE;
    }
}
