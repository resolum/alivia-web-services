package com.resolum.alivia.iam.infrastructure.authorization.sfs.configuration;

import com.resolum.alivia.iam.infrastructure.authorization.sfs.pipeline.BearerAuthorizationRequestFilter;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.pipeline.UnauthorizedRequestHandlerEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

/**
 * HTTP security of the API: stateless JWT bearer authentication, CORS and the list of
 * public endpoints. Every other endpoint requires an authenticated user.
 */
@Configuration
@EnableMethodSecurity
public class WebSecurityConfiguration {

    private final BearerAuthorizationRequestFilter bearerFilter;
    private final UnauthorizedRequestHandlerEntryPoint unauthorizedEntryPoint;
    private final List<String> allowedOrigins;

    /**
     * Creates the configuration.
     *
     * @param bearerFilter           the bearer token filter
     * @param unauthorizedEntryPoint the 401 handler
     * @param allowedOrigins         the origin patterns allowed by CORS
     */
    public WebSecurityConfiguration(BearerAuthorizationRequestFilter bearerFilter,
                                    UnauthorizedRequestHandlerEntryPoint unauthorizedEntryPoint,
                                    @Value("${alivia.cors.allowed-origins}") List<String> allowedOrigins) {
        this.bearerFilter = bearerFilter;
        this.unauthorizedEntryPoint = unauthorizedEntryPoint;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * Builds the security filter chain.
     *
     * @param http the security builder
     * @return the security filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(configurer -> configurer.configurationSource(request -> {
                    var cors = new CorsConfiguration();
                    cors.setAllowedOriginPatterns(allowedOrigins);
                    cors.setAllowedMethods(List.of("GET", "PATCH", "POST", "PUT", "DELETE", "OPTIONS"));
                    cors.setAllowedHeaders(List.of("*"));
                    cors.setExposedHeaders(List.of("Authorization"));
                    cors.setMaxAge(3600L);
                    return cors;
                }))
                // Stateless bearer tokens and no session cookies, so there is no CSRF vector.
                .csrf(AbstractHttpConfigurer::disable) // NOSONAR
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthorizedEntryPoint))
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/plans", "/api/v1/plans/*", "/api/v1/plans/*/pricing").permitAll()
                        // The guided contracting flow builds the draft and its payment before the account
                        // exists; drafts are reached only through their random UUID.
                        .requestMatchers("/api/v1/configuration-drafts", "/api/v1/configuration-drafts/**").permitAll()
                        // The same guest books the technical evaluation of that draft.
                        .requestMatchers(HttpMethod.GET, "/api/v1/appointments/available-slots").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/appointments").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/stripe").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
