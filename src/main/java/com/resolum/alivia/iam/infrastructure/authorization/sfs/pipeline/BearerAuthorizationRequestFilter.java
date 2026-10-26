package com.resolum.alivia.iam.infrastructure.authorization.sfs.pipeline;

import com.resolum.alivia.iam.application.internal.outboundservices.tokens.TokenService;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.model.UsernamePasswordAuthenticationTokenBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests that carry a valid {@code Authorization: Bearer} access token.
 *
 * <p>An invalid, expired or missing token leaves the request anonymous; protected
 * endpoints then answer 401 through {@link UnauthorizedRequestHandlerEntryPoint}.</p>
 */
@Slf4j
@Component
public class BearerAuthorizationRequestFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final UserDetailsService userDetailsService;

    /**
     * Creates the filter.
     *
     * @param tokenService       the access token service
     * @param userDetailsService the user loader
     */
    public BearerAuthorizationRequestFilter(TokenService tokenService, UserDetailsService userDetailsService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Validates the bearer token and stores the authenticated user in the security context.
     *
     * @param request     the HTTP request
     * @param response    the HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException if a later filter fails
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var userId = tokenService.getUserIdFromAccessToken(header.substring(BEARER_PREFIX.length()));
                var userDetails = userDetailsService.loadUserByUsername(userId);
                // Blocked or inactive accounts lose access before their token expires.
                if (userDetails.isEnabled()) {
                    SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationTokenBuilder.build(userDetails, request));
                }
            } catch (RuntimeException exception) {
                log.debug("Access token rejected: {}", exception.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
