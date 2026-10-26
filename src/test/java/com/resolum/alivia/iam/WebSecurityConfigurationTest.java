package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.outboundservices.tokens.TokenService;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.configuration.WebSecurityConfiguration;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.pipeline.BearerAuthorizationRequestFilter;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.pipeline.UnauthorizedRequestHandlerEntryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(WebSecurityConfigurationTest.TestConfig.class)
@TestPropertySource(properties = "alivia.cors.allowed-origins=https://app.alivia.com")
class WebSecurityConfigurationTest {

    private static final String PRIVATE_PATH = "/api/v1/private";
    private static final String BEARER = "Bearer access-token";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserDetailsService userDetailsService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(tokenService, userDetailsService);
        SecurityContextHolder.clearContext();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void publicEndpointIsReachableWithoutToken() throws Exception {
        mvc.perform(get("/api/v1/auth/ping")).andExpect(status().isOk());
    }

    @Test
    void privateEndpointWithoutTokenReturns401() throws Exception {
        mvc.perform(get(PRIVATE_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PRIVATE_PATH).header(HttpHeaders.AUTHORIZATION, "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validBearerTokenOfEnabledUserIsAuthenticated() throws Exception {
        givenUser(true);

        mvc.perform(get(PRIVATE_PATH).header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isOk());
        // CSRF is disabled: a POST with only the bearer token passes.
        mvc.perform(post(PRIVATE_PATH).header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isOk());
    }

    @Test
    void alreadyAuthenticatedRequestSkipsTokenParsing() throws Exception {
        mvc.perform(get(PRIVATE_PATH).with(user("7")).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk());

        verifyNoInteractions(tokenService);
    }

    @Test
    void disabledUserIsRejected() throws Exception {
        givenUser(false);

        mvc.perform(get(PRIVATE_PATH).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        when(tokenService.getUserIdFromAccessToken("access-token")).thenThrow(new IllegalArgumentException("bad"));

        mvc.perform(get(PRIVATE_PATH).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options(PRIVATE_PATH)
                        .header(HttpHeaders.ORIGIN, "https://app.alivia.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.alivia.com"));
        mvc.perform(options(PRIVATE_PATH)
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    private void givenUser(boolean enabled) {
        when(tokenService.getUserIdFromAccessToken("access-token")).thenReturn("42");
        when(userDetailsService.loadUserByUsername("42"))
                .thenReturn(User.withUsername("42").password("hash").roles("CAREGIVER").disabled(!enabled).build());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({WebSecurityConfiguration.class, BearerAuthorizationRequestFilter.class,
            UnauthorizedRequestHandlerEntryPoint.class, TestEndpoints.class})
    static class TestConfig {

        @Bean
        TokenService tokenService() {
            return mock(TokenService.class);
        }

        @Bean
        UserDetailsService userDetailsService() {
            return mock(UserDetailsService.class);
        }
    }

    @RestController
    static class TestEndpoints {

        @GetMapping("/api/v1/auth/ping")
        String ping() {
            return "pong";
        }

        @GetMapping(PRIVATE_PATH)
        String read() {
            return "ok";
        }

        @PostMapping(PRIVATE_PATH)
        String write() {
            return "ok";
        }
    }
}
