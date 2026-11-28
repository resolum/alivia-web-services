package com.resolum.alivia.tracking.interfaces.websocket;

import com.resolum.alivia.tracking.domain.model.events.DeviceEventRecordedEvent;
import com.resolum.alivia.tracking.interfaces.rest.transform.RecentEventsResourceAssembler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The live history of a home: each event kept from an edge (or produced by the server) is pushed, as the history shows
 * it, to everyone following that home. A client loads the history over REST first and then only listens here; the
 * channel carries nothing the REST history does not.
 *
 * <p>Messages: {@code {"kind":"connected","personId":..}} once, then {@code {"kind":"event","personId":..,"event":{..}}}.
 * A text {@code ping} is answered with {@code pong}.</p>
 */
@Slf4j
@Component
public class HomeEventsWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_LIMIT_BYTES = 256 * 1024;

    private final JsonMapper json;
    private final Map<Long, Set<WebSocketSession>> byHousehold = new ConcurrentHashMap<>();

    public HomeEventsWebSocketHandler(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        var householdId = (Long) session.getAttributes().get(HomeEventsHandshakeInterceptor.HOUSEHOLD);
        var safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);
        session.getAttributes().put("safe", safe);
        byHousehold.computeIfAbsent(householdId, id -> ConcurrentHashMap.newKeySet()).add(safe);
        safe.sendMessage(new TextMessage(json.writeValueAsString(Map.of("kind", "connected",
                "personId", session.getAttributes().get(HomeEventsHandshakeInterceptor.PERSON)))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if ("ping".equalsIgnoreCase(message.getPayload().strip())) {
            safe(session).sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        forget(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        forget(session);
    }

    /** Pushes the event to whoever follows its home. */
    @EventListener
    public void on(DeviceEventRecordedEvent recorded) {
        var event = recorded.event();
        var sessions = event.householdId() == null ? null : byHousehold.get(event.householdId());
        if (sessions == null || sessions.isEmpty()) return;
        var resource = RecentEventsResourceAssembler.toResource(event);
        for (var session : sessions) {
            try {
                var body = new LinkedHashMap<String, Object>();
                body.put("kind", "event");
                body.put("personId", session.getAttributes().get(HomeEventsHandshakeInterceptor.PERSON));
                body.put("event", resource);
                session.sendMessage(new TextMessage(json.writeValueAsString(body)));
            } catch (Exception failure) {
                log.debug("A live history session could not be told of {}: {}", event.eventId(), failure.getMessage());
                sessions.remove(session);
            }
        }
    }

    /** How many are following the home now. */
    int followersOf(Long householdId) {
        var sessions = byHousehold.get(householdId);
        return sessions == null ? 0 : sessions.size();
    }

    private static WebSocketSession safe(WebSocketSession session) {
        return session.getAttributes().get("safe") instanceof WebSocketSession safe ? safe : session;
    }

    private void forget(WebSocketSession session) {
        var householdId = (Long) session.getAttributes().get(HomeEventsHandshakeInterceptor.HOUSEHOLD);
        var sessions = householdId == null ? null : byHousehold.get(householdId);
        if (sessions != null) sessions.remove(safe(session));
    }
}
