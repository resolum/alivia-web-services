package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.EdgeNotFound;
import com.resolum.alivia.assets.domain.exceptions.EdgeRetired;
import com.resolum.alivia.assets.domain.model.commands.IssueEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.commands.RevokeEdgeCredentialCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.model.valueobjects.IssuedEdgeCredential;
import com.resolum.alivia.assets.domain.services.EdgeCredentialCommandService;
import com.resolum.alivia.assets.interfaces.rest.controllers.EdgeCredentialsController;
import com.resolum.alivia.iam.domain.exceptions.EdgeCredentialConflictException;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EdgeCredentialsControllerTest {

    private static final String PATH = "/api/v1/edges/7/credential";
    private static final IssueEdgeCredentialCommand ISSUE = new IssueEdgeCredentialCommand(7L);
    private static final RevokeEdgeCredentialCommand REVOKE = new RevokeEdgeCredentialCommand(7L);

    private EdgeCredentialCommandService commands;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        commands = mock(EdgeCredentialCommandService.class);
        mvc = MockMvcBuilders.standaloneSetup(new EdgeCredentialsController(commands))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void issuesACredentialAndForbidsCachingTheSecret() throws Exception {
        when(commands.handle(ISSUE)).thenReturn(new IssuedEdgeCredential(7L, EdgeType.MICROPHONE_EDGE, "7.the-secret"));

        mvc.perform(post(PATH))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.edgeId").value(7))
                .andExpect(jsonPath("$.edgeType").value("MICROPHONE_EDGE"))
                .andExpect(jsonPath("$.credential").value("7.the-secret"));
    }

    @Test
    void answers404ForAMissingEdge() throws Exception {
        doThrow(new EdgeNotFound(7L)).when(commands).handle(ISSUE);

        mvc.perform(post(PATH)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("edge_not_found"));
    }

    @Test
    void answers409ForARetiredEdgeAndForARaceBetweenFirstCredentials() throws Exception {
        doThrow(new EdgeRetired(7L)).when(commands).handle(ISSUE);
        mvc.perform(post(PATH)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("edge_retired"));

        doThrow(new EdgeCredentialConflictException()).when(commands).handle(ISSUE);
        mvc.perform(post(PATH)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("edge_credential_conflict"));
    }

    @Test
    void anErrorResponseNeverCarriesACredential() throws Exception {
        doThrow(new EdgeRetired(7L)).when(commands).handle(ISSUE);

        mvc.perform(post(PATH)).andExpect(content().string(not(containsString("\"credential\""))));
    }

    @Test
    void revokesACredentialWithNoContent() throws Exception {
        mvc.perform(delete(PATH)).andExpect(status().isNoContent()).andExpect(content().string(""));

        verify(commands).handle(REVOKE);
    }

    @Test
    void revokingTheCredentialOfAMissingEdgeAnswers404() throws Exception {
        doThrow(new EdgeNotFound(7L)).when(commands).handle(REVOKE);

        mvc.perform(delete(PATH)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("edge_not_found"));
    }
}
