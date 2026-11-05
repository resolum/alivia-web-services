package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.valueobjects.CallerAuthority;
import org.springframework.security.core.Authentication;

public class CallerAuthorityFromAuthenticationAssembler {
    private static final String ADMINISTRATOR_ROLE = "ROLE_ADMINISTRATOR";

    /**
     * The administrator of the business may act on any household; everyone else acts as a household member and must
     * manage it. The work of technicians goes through Operations, not through these endpoints.
     */
    public static CallerAuthority toCallerAuthority(Authentication authentication) {
        var administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMINISTRATOR_ROLE.equals(authority.getAuthority()));
        return administrator ? CallerAuthority.BUSINESS_ADMINISTRATOR : CallerAuthority.HOUSEHOLD_MEMBER;
    }
}
