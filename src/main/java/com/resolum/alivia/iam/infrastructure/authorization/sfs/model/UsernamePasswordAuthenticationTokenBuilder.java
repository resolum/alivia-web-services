package com.resolum.alivia.iam.infrastructure.authorization.sfs.model;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

/**
 * Builds authenticated {@link UsernamePasswordAuthenticationToken} instances.
 */
public final class UsernamePasswordAuthenticationTokenBuilder {

    private UsernamePasswordAuthenticationTokenBuilder() {
    }

    /**
     * Builds an authenticated token with the user authorities and the request details.
     *
     * @param userDetails the authenticated user
     * @param request     the current HTTP request
     * @return the authenticated token
     */
    public static UsernamePasswordAuthenticationToken build(UserDetails userDetails, HttpServletRequest request) {
        var token = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return token;
    }
}
