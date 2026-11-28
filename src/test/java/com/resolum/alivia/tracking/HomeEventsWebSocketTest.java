package com.resolum.alivia.tracking;

import com.resolum.alivia.tracking.domain.exceptions.AssistedPersonNotAccessible;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.events.DeviceEventRecordedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.services.TrackingAccessService;
import com.resolum.alivia.tracking.interfaces.websocket.HomeEventsHandshakeInterceptor;
import com.resolum.alivia.tracking.interfaces.websocket.HomeEventsWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeEventsWebSocketTest {

    private static final Instant T0 = Instant.parse("2026-11-27T15:00:00Z");

    private final TrackingAccessService access = mock(TrackingAccessService.class);
    private final HomeEventsHandshakeInterceptor interceptor = new HomeEventsHandshakeInterceptor(access);
    private final HomeEventsWebSocketHandler handler = new HomeEventsWebSocketHandler(JsonMapper.builder().build());

    @Test
    void the_handshake_keeps_the_home_the_user_may_read() {
        when(access.requireReadableHouseholdOfPerson(7L, 5L)).thenReturn(300L);
        var attributes = new HashMap<String, Object>();
        var accepted = interceptor.beforeHandshake(request("/ws/v1/assisted-persons/5/events", "7"),
                mock(ServerHttpResponse.class), handler, attributes);
        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry("householdId", 300L).containsEntry("personId", 5L);
    }

    @Test
    void a_person_the_user_may_not_read_is_not_found() {
        when(access.requireReadableHouseholdOfPerson(7L, 6L)).thenThrow(new AssistedPersonNotAccessible(6L));
        var response = mock(ServerHttpResponse.class);
        var accepted = interceptor.beforeHandshake(request("/ws/v1/assisted-persons/6/events", "7"), response, handler,
                new HashMap<>());
        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.NOT_FOUND);
    }

    @Test
    void an_edge_credential_cannot_follow_a_home() {
        var response = mock(ServerHttpResponse.class);
        var accepted = interceptor.beforeHandshake(request("/ws/v1/assisted-persons/5/events", "edge-3"), response,
                handler, new HashMap<>());
        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void each_kept_event_reaches_only_the_followers_of_its_home() throws Exception {
        var mine = new ArrayList<String>();
        var other = new ArrayList<String>();
        var session = session(300L, 5L, mine);
        handler.afterConnectionEstablished(session);
        handler.afterConnectionEstablished(session(400L, 9L, other));

        handler.on(new DeviceEventRecordedEvent(DeviceEvent.byServer("dc-edge-3-1", EventType.EDGE_DISCONNECTED, 3L,
                30L, 300L, null, T0, "Sin reportes")));

        assertThat(mine).hasSize(2);
        assertThat(mine.get(0)).contains("\"kind\":\"connected\"");
        assertThat(mine.get(1)).contains("\"kind\":\"event\"", "\"personId\":5", "\"type\":\"EDGE_DISCONNECTED\"",
                "\"occurredAt\":\"2026-11-27T15:00:00Z\"");
        assertThat(other).hasSize(1);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        handler.on(new DeviceEventRecordedEvent(DeviceEvent.byServer("dc-edge-3-2", EventType.EDGE_DISCONNECTED, 3L,
                30L, 300L, null, T0, null)));
        assertThat(mine).hasSize(2);
    }

    private static ServerHttpRequest request(String path, String principal) {
        var request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost" + path));
        when(request.getPrincipal()).thenReturn(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        return request;
    }

    private static WebSocketSession session(Long householdId, Long personId, List<String> sent) throws Exception {
        var session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>(Map.of("householdId", householdId, "personId", personId));
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("s-" + householdId);
        doAnswer(call -> {
            WebSocketMessage<?> message = call.getArgument(0);
            sent.add(((TextMessage) message).getPayload());
            return null;
        }).when(session).sendMessage(any());
        return session;
    }
}
