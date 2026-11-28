package com.resolum.alivia.tracking.interfaces.websocket;

import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonNotAccessible;
import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonWithoutHousehold;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import com.resolum.alivia.tracking.interfaces.rest.transform.UserIdFromAuthentication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Lets a user follow live only a home they may read: the same rule as the history over REST. The user comes from the
 * bearer token of the handshake (the security filters already checked it), never from the request.
 */
@Slf4j
@Component
public class HomeEventsHandshakeInterceptor implements HandshakeInterceptor {

    static final String HOUSEHOLD = "householdId";
    static final String PERSON = "personId";
    private static final Pattern PATH = Pattern.compile(".*/assisted-persons/(\\d{1,18})/events/?$");

    private final TrackingAccessService access;

    public HomeEventsHandshakeInterceptor(TrackingAccessService access) {
        this.access = access;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        var matcher = PATH.matcher(request.getURI().getPath());
        if (!matcher.matches()) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
        var personId = Long.parseLong(matcher.group(1));
        try {
            var authentication = request.getPrincipal() instanceof Authentication principal ? principal
                    : SecurityContextHolder.getContext().getAuthentication();
            var userId = UserIdFromAuthentication.of(authentication);
            attributes.put(HOUSEHOLD, access.requireReadableHouseholdOfPerson(userId, personId));
            attributes.put(PERSON, personId);
            return true;
        } catch (AccessDeniedException denied) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
        } catch (AssistedPersonNotAccessible | AssistedPersonWithoutHousehold notReadable) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
        // nothing to do
    }
}
