package com.resolum.alivia.tracking.interfaces.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * {@code /ws/v1/assisted-persons/{personId}/events}: the live history of the home of an assisted person. The handshake
 * carries {@code Authorization: Bearer <access token>} like any other call; no cookie is used, so any origin may open it.
 */
@Configuration
@EnableWebSocket
public class TrackingWebSocketConfiguration implements WebSocketConfigurer {

    public static final String PATH = "/ws/v1/assisted-persons/*/events";

    private final HomeEventsWebSocketHandler handler;
    private final HomeEventsHandshakeInterceptor interceptor;

    public TrackingWebSocketConfiguration(HomeEventsWebSocketHandler handler, HomeEventsHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, PATH).addInterceptors(interceptor).setAllowedOriginPatterns("*");
    }
}
