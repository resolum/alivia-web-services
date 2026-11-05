package com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Keeps the JPA session open for the whole web request (the behavior the application always had, which its endpoints
 * may rely on), except for the endpoints of the edge services.
 *
 * <p>An edge service that is up to date waits for the next version of its table (long polling). With the session open
 * during that wait every waiting request would hold a database connection for up to 25 seconds, and the pool (10
 * connections) would be exhausted by a few dozen edges, taking the whole API down with it. The edge endpoints load
 * everything inside their own transactions and need no session afterwards, so they are left out.</p>
 *
 * <p>Spring Boot's own switch ({@code spring.jpa.open-in-view}) is all or nothing, so it is turned off in
 * {@code application.properties} and this configuration registers the same interceptor for every other path.</p>
 */
@Configuration
public class OpenInViewConfiguration implements WebMvcConfigurer {

    /** Paths of the edge services: no session is kept open for them. */
    public static final String EDGE_PATHS = "/api/v1/edge/**";

    private final EntityManagerFactory entityManagerFactory;

    public OpenInViewConfiguration(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var interceptor = new OpenEntityManagerInViewInterceptor();
        interceptor.setEntityManagerFactory(entityManagerFactory);
        registry.addWebRequestInterceptor(interceptor).excludePathPatterns(EDGE_PATHS);
    }
}
