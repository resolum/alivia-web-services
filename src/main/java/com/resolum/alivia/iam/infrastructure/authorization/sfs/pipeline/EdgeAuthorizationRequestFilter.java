package com.resolum.alivia.iam.infrastructure.authorization.sfs.pipeline;

import com.resolum.alivia.iam.domain.model.commands.AuthenticateEdgeCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AuthenticatedEdge;
import com.resolum.alivia.iam.domain.services.EdgeCredentialCommandService;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.model.EdgeAuthorities;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates the edge services that call {@code /api/v1/edge/**} with their credential as bearer token.
 *
 * <p>It only looks at those paths, so a credential sent anywhere else is ignored and the request stays anonymous. A
 * missing, malformed, unknown or revoked credential also leaves the request anonymous; the protected endpoint then
 * answers 401 through {@link UnauthorizedRequestHandlerEntryPoint}, with no hint of which part was wrong. The caller is
 * named {@code edge:<id>} and holds the authority of its kind ({@link EdgeAuthorities}).</p>
 *
 * <p>A check that cannot be done (the database is down, for example) is not a bad credential and is not answered as one:
 * it is logged as a warning and answered {@code 503} with {@code Retry-After}, which the edge services retry. Answering
 * 401 would look like every credential had been revoked, and the cause would stay hidden.</p>
 *
 * <p>A source that presents too many wrong credentials is refused with {@code 429} and {@code Retry-After} before any of
 * this work ({@link EdgeFailedAttemptsLimiter}).</p>
 */
@Slf4j
@Component
public class EdgeAuthorizationRequestFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    /** What the edge is told to wait before asking again when the credential could not be checked. */
    private static final int RETRY_AFTER_SECONDS = 5;
    private static final RequestMatcher EDGE_PATHS =
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/edge/**");

    private final SecurityContextRepository contextRepository = new RequestAttributeSecurityContextRepository();
    private final EdgeCredentialCommandService credentialService;
    private final EdgeFailedAttemptsLimiter failedAttempts;

    public EdgeAuthorizationRequestFilter(EdgeCredentialCommandService credentialService,
                                          EdgeFailedAttemptsLimiter failedAttempts) {
        this.credentialService = credentialService;
        this.failedAttempts = failedAttempts;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !EDGE_PATHS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            var source = request.getRemoteAddr();
            var wait = failedAttempts.secondsToWait(source);
            if (wait > 0) {
                log.warn("Too many rejected edge credentials from {}; refusing it for {} s", source, wait);
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(wait));
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests");
                return;
            }
            Optional<AuthenticatedEdge> authenticated;
            try {
                authenticated = credentialService.handle(new AuthenticateEdgeCommand(header.substring(BEARER_PREFIX.length())));
            } catch (RuntimeException failure) {
                log.warn("Could not check the credential of an edge: {}", failure.toString());
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER_SECONDS));
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service Unavailable");
                return;
            }
            authenticated.ifPresentOrElse(edge -> authenticate(edge, request, response), () -> {
                failedAttempts.recordFailure(source);
                log.debug("Edge credential rejected");
            });
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(AuthenticatedEdge edge, HttpServletRequest request, HttpServletResponse response) {
        var authentication = new UsernamePasswordAuthenticationToken("edge:" + edge.edgeId(), null,
                List.of(new SimpleGrantedAuthority(EdgeAuthorities.of(edge.edgeKind()))));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // A request that waits (long polling) is dispatched again when it finishes, and the security chain then loads the
        // context from the request, not from this thread: without this the answer of an edge that waited would be refused
        // as anonymous.
        contextRepository.saveContext(context, request, response);
    }
}
