package com.resolum.alivia.shared;

import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.OpenInViewConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** The JPA session stays open for every request except the ones of the edge services, which wait. */
class OpenInViewConfigurationTest {

    /** Exposes the interceptors that the configuration registers. */
    private static final class Registry extends InterceptorRegistry {
        java.util.List<Object> registered() {
            return getInterceptors();
        }
    }

    private static boolean appliesTo(MappedInterceptor interceptor, String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        ServletRequestPathUtils.parseAndCache(request);
        return interceptor.matches(request);
    }

    private MappedInterceptor registered() {
        var registry = new Registry();
        new OpenInViewConfiguration(mock(EntityManagerFactory.class)).addInterceptors(registry);
        assertEquals(1, registry.registered().size());
        var interceptor = (MappedInterceptor) registry.registered().get(0);
        assertInstanceOf(OpenEntityManagerInViewInterceptor.class, interceptor.getInterceptor() == null
                ? null : unwrap(interceptor));
        return interceptor;
    }

    private static Object unwrap(MappedInterceptor interceptor) {
        // The web request interceptor is wrapped in an adapter; the adapter keeps it as its only field.
        var adapter = interceptor.getInterceptor();
        for (var field : adapter.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                var value = field.get(adapter);
                if (value instanceof OpenEntityManagerInViewInterceptor) {
                    return value;
                }
            } catch (IllegalAccessException exception) {
                throw new AssertionError(exception);
            }
        }
        return null;
    }

    @Test
    void theSessionIsKeptOpenForTheOrdinaryEndpoints() {
        var interceptor = registered();

        assertTrue(appliesTo(interceptor, "/api/v1/devices"));
        assertTrue(appliesTo(interceptor, "/api/v1/edges/7/credential"));
        assertTrue(appliesTo(interceptor, "/api/v1/installations/3/edge-sync"));
        assertTrue(appliesTo(interceptor, "/api/v1/auth/sign-in"));
    }

    @Test
    void theEdgeServicesThatWaitHoldNoSessionAndNoConnection() {
        var interceptor = registered();

        assertFalse(appliesTo(interceptor, "/api/v1/edge/device-table"));
        assertFalse(appliesTo(interceptor, "/api/v1/edge/sync-state"));
    }
}
