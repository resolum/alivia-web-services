package com.resolum.alivia.iam;

import com.resolum.alivia.iam.application.internal.outboundservices.tokens.TokenService;
import com.resolum.alivia.iam.domain.model.commands.AuthenticateEdgeCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AuthenticatedEdge;
import com.resolum.alivia.iam.domain.model.valueobjects.EdgeKind;
import com.resolum.alivia.iam.domain.services.EdgeCredentialCommandService;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.configuration.WebSecurityConfiguration;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.pipeline.BearerAuthorizationRequestFilter;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.pipeline.EdgeAuthorizationRequestFilter;
import com.resolum.alivia.iam.infrastructure.authorization.sfs.pipeline.UnauthorizedRequestHandlerEntryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** How the edge services authenticate against the real security chain. */
@SpringJUnitWebConfig(EdgeSecurityTest.TestConfig.class)
@TestPropertySource(properties = "alivia.cors.allowed-origins=https://app.alivia.com")
class EdgeSecurityTest {

    private static final String EDGE_PATH = "/api/v1/edge/whoami";
    private static final String USER_PATH = "/api/v1/private";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private EdgeCredentialCommandService credentials;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserDetailsService userDetailsService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(credentials, tokenService, userDetailsService);
        SecurityContextHolder.clearContext();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        when(credentials.handle(any(AuthenticateEdgeCommand.class))).thenReturn(Optional.empty());
    }

    @Test
    void aMicrophoneEdgeIsNamedAfterItsIdAndHoldsTheMicrophoneAuthority() throws Exception {
        givenEdge("7.microphone-secret", new AuthenticatedEdge(7L, EdgeKind.MICROPHONE));

        mvc.perform(get(EDGE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer 7.microphone-secret"))
                .andExpect(status().isOk())
                .andExpect(content().string("edge:7 [ROLE_EDGE_MICROPHONE]"));
    }

    @Test
    void anActuatorEdgeHoldsADifferentAuthority() throws Exception {
        givenEdge("8.actuator-secret", new AuthenticatedEdge(8L, EdgeKind.ACTUATOR));

        mvc.perform(get(EDGE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer 8.actuator-secret"))
                .andExpect(status().isOk())
                .andExpect(content().string("edge:8 [ROLE_EDGE_ACTUATOR]"));
    }

    @Test
    void anUnknownWrongOrRevokedCredentialAnswers401() throws Exception {
        mvc.perform(get(EDGE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer 7.wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noCredentialOrAnotherSchemeAnswers401WithoutLookingAnythingUp() throws Exception {
        mvc.perform(get(EDGE_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(EDGE_PATH).header(HttpHeaders.AUTHORIZATION, "Basic abc")).andExpect(status().isUnauthorized());

        verifyNoInteractions(credentials);
    }

    @Test
    void aFailureCheckingTheCredentialAnswers401InsteadOfFailing() throws Exception {
        when(credentials.handle(any(AuthenticateEdgeCommand.class))).thenThrow(new IllegalStateException("db down"));

        mvc.perform(get(EDGE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer 7.secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aUserCannotUseTheEdgeEndpointsEvenWithAValidToken() throws Exception {
        when(tokenService.getUserIdFromAccessToken("user-token")).thenReturn("42");
        when(userDetailsService.loadUserByUsername("42"))
                .thenReturn(User.withUsername("42").password("hash").roles("ADMINISTRATOR").build());

        mvc.perform(get(EDGE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEdgeCredentialDoesNotOpenAnythingOutsideTheEdgeEndpoints() throws Exception {
        mvc.perform(get(USER_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer 7.microphone-secret"))
                .andExpect(status().isUnauthorized());

        // The edge filter ignores those paths: the credential is never checked there.
        verifyNoInteractions(credentials);
    }

    @Test
    void anEdgeThatWaitedIsStillAuthenticatedWhenItsAnswerIsDispatched() throws Exception {
        givenEdge("7.microphone-secret", new AuthenticatedEdge(7L, EdgeKind.MICROPHONE));
        var pending = new CompletableFuture<String>();
        TestEndpoints.pending = pending;

        var started = mvc.perform(get("/api/v1/edge/wait").header(HttpHeaders.AUTHORIZATION, "Bearer 7.microphone-secret"))
                .andExpect(request().asyncStarted()).andReturn();
        pending.complete("woken");

        // The answer goes through the security chain again, which loads the context from the request.
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andExpect(content().string("woken"));
    }

    @Test
    void anEdgeThatWaitedWithoutCredentialNeverGetsPast401() throws Exception {
        TestEndpoints.pending = new CompletableFuture<>();

        mvc.perform(get("/api/v1/edge/wait")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/edge/wait").header(HttpHeaders.AUTHORIZATION, "Bearer 7.wrong")).andExpect(status().isUnauthorized());
    }

    private void givenEdge(String presented, AuthenticatedEdge edge) {
        when(credentials.handle(new AuthenticateEdgeCommand(presented))).thenReturn(Optional.of(edge));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({WebSecurityConfiguration.class, BearerAuthorizationRequestFilter.class,
            EdgeAuthorizationRequestFilter.class, UnauthorizedRequestHandlerEntryPoint.class, TestEndpoints.class})
    static class TestConfig {

        @Bean
        EdgeCredentialCommandService edgeCredentialCommandService() {
            return mock(EdgeCredentialCommandService.class);
        }

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

        @GetMapping(EDGE_PATH)
        String whoAmI(Authentication authentication) {
            return authentication.getName() + " " + authentication.getAuthorities();
        }

        static volatile CompletableFuture<String> pending;

        @GetMapping("/api/v1/edge/wait")
        CompletableFuture<String> waitForIt() {
            return pending;
        }

        @GetMapping(USER_PATH)
        String user() {
            return "ok";
        }
    }
}
