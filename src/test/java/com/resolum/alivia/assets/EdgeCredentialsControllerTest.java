package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.EdgeRetired;
import com.resolum.alivia.assets.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.commands.RevokeEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.model.valueobjects.IssuedEdgeCredential;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeCredentialStatusQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeCredentialStatus;
import com.resolum.alivia.assets.domain.services.EdgeCredentialCommandService;
import com.resolum.alivia.assets.domain.services.EdgeCredentialQueryService;
import com.resolum.alivia.assets.interfaces.rest.controllers.EdgeCredentialsController;
import com.resolum.alivia.iam.domain.exceptions.EdgeCredentialConflictException;
import java.time.LocalDateTime;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EdgeCredentialsControllerTest {

    private static final String PATH = "/api/v1/edges/7/credential";
    private static final IssueEdgeCredentialCommand ISSUE = new IssueEdgeCredentialCommand(7L, 3L);
    private static final RevokeEdgeCredentialCommand REVOKE = new RevokeEdgeCredentialCommand(7L, 3L);

    private static final Authentication ADMIN = new UsernamePasswordAuthenticationToken("3", null);

    private EdgeCredentialCommandService commands;
    private EdgeCredentialQueryService queries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        commands = mock(EdgeCredentialCommandService.class);
        queries = mock(EdgeCredentialQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new EdgeCredentialsController(commands, queries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void issuesACredentialAndForbidsCachingTheSecret() throws Exception {
        when(commands.handle(ISSUE)).thenReturn(new IssuedEdgeCredential(7L, EdgeType.MICROPHONE_EDGE, "7.the-secret"));

        mvc.perform(post(PATH).principal(ADMIN))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.edgeId").value(7))
                .andExpect(jsonPath("$.edgeType").value("MICROPHONE_EDGE"))
                .andExpect(jsonPath("$.credential").value("7.the-secret"));
    }

    @Test
    void answers404ForAMissingEdge() throws Exception {
        doThrow(new EdgeNotFound(7L)).when(commands).handle(ISSUE);

        mvc.perform(post(PATH).principal(ADMIN)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("edge_not_found"));
    }

    @Test
    void answers409ForARetiredEdgeAndForARaceBetweenFirstCredentials() throws Exception {
        doThrow(new EdgeRetired(7L)).when(commands).handle(ISSUE);
        mvc.perform(post(PATH).principal(ADMIN)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("edge_retired"));

        doThrow(new EdgeCredentialConflictException()).when(commands).handle(ISSUE);
        mvc.perform(post(PATH).principal(ADMIN)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("edge_credential_conflict"));
    }

    @Test
    void anErrorResponseNeverCarriesACredential() throws Exception {
        doThrow(new EdgeRetired(7L)).when(commands).handle(ISSUE);

        mvc.perform(post(PATH).principal(ADMIN)).andExpect(content().string(not(containsString("\"credential\""))));
    }

    @Test
    void revokesACredentialWithNoContent() throws Exception {
        mvc.perform(delete(PATH).principal(ADMIN)).andExpect(status().isNoContent()).andExpect(content().string(""));

        verify(commands).handle(REVOKE);
    }

    @Test
    void theAdministratorBehindARequestIsTheOneWhoIssuesAndRevokes() throws Exception {
        when(commands.handle(any(IssueEdgeCredentialCommand.class)))
                .thenReturn(new IssuedEdgeCredential(7L, EdgeType.MICROPHONE_EDGE, "7.the-secret"));
        var other = new UsernamePasswordAuthenticationToken("11", null);

        mvc.perform(post(PATH).principal(other)).andExpect(status().isCreated());
        mvc.perform(delete(PATH).principal(other)).andExpect(status().isNoContent());

        verify(commands).handle(new IssueEdgeCredentialCommand(7L, 11L));
        verify(commands).handle(new RevokeEdgeCredentialCommand(7L, 11L));
    }

    @Test
    void showsWhoIssuedTheCredentialItsAgeAndWhetherToRotateItWithoutTheSecret() throws Exception {
        var issuedAt = LocalDateTime.of(2025, 10, 1, 8, 0);
        when(queries.handle(new GetEdgeCredentialStatusQuery(7L))).thenReturn(new EdgeCredentialStatus(7L,
                EdgeCredentialStatus.State.ACTIVE, issuedAt, 3L, 400L, true, LocalDateTime.of(2026, 11, 5, 11, 0), null, null, null));

        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edgeId").value(7))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.issuedBy").value(3))
                .andExpect(jsonPath("$.ageDays").value(400))
                .andExpect(jsonPath("$.rotationRecommended").value(true))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("credential\""))));
    }

    @Test
    void showsWhoAndWhyARevokedCredentialWasRevoked() throws Exception {
        when(queries.handle(new GetEdgeCredentialStatusQuery(7L))).thenReturn(new EdgeCredentialStatus(7L,
                EdgeCredentialStatus.State.REVOKED, LocalDateTime.of(2026, 1, 1, 8, 0), 3L, 308L, false, null,
                LocalDateTime.of(2026, 11, 4, 9, 0), null, "EDGE_RETIRED"));

        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REVOKED"))
                .andExpect(jsonPath("$.revokedReason").value("EDGE_RETIRED"))
                .andExpect(jsonPath("$.revokedBy").doesNotExist());
    }

    @Test
    void anEdgeWithoutCredentialAnswers200NotIssuedAndAMissingEdgeAnswers404() throws Exception {
        when(queries.handle(new GetEdgeCredentialStatusQuery(7L))).thenReturn(EdgeCredentialStatus.notIssued(7L));
        when(queries.handle(new GetEdgeCredentialStatusQuery(9L))).thenThrow(new EdgeNotFound(9L));

        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("NOT_ISSUED"))
                .andExpect(jsonPath("$.issuedAt").doesNotExist());
        mvc.perform(get("/api/v1/edges/9/credential")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("edge_not_found"));
    }

    @Test
    void revokingTheCredentialOfAMissingEdgeAnswers404() throws Exception {
        doThrow(new EdgeNotFound(7L)).when(commands).handle(REVOKE);

        mvc.perform(delete(PATH).principal(ADMIN)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("edge_not_found"));
    }
}
