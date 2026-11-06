package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.AppliedVersionAhead;
import com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation;
import com.resolum.alivia.assets.domain.model.commands.AcknowledgeEdgeVersionCommand;
import com.resolum.alivia.assets.domain.model.queries.GetInstallationEdgeSyncQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.AppliedEdgeVersion;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.domain.model.valueobjects.InstallationEdgeSync;
import com.resolum.alivia.assets.domain.services.EdgeSyncCommandService;
import com.resolum.alivia.assets.domain.services.EdgeSyncQueryService;
import com.resolum.alivia.assets.interfaces.rest.controllers.EdgeSyncStateController;
import com.resolum.alivia.assets.interfaces.rest.controllers.InstallationEdgeSyncController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The confirmation an edge sends and the view of the administrator. */
class EdgeSyncControllersTest {

    private static final String SYNC = "/api/v1/edge/sync-state";
    private static final Authentication EDGE = new TestingAuthenticationToken("edge:7", null, "ROLE_EDGE_ACTUATOR");
    private static final LocalDateTime ACKED = LocalDateTime.of(2026, 11, 5, 12, 0);

    private EdgeSyncCommandService commands;
    private EdgeSyncQueryService queries;
    private MockMvc stateMvc;
    private MockMvc viewMvc;

    @BeforeEach
    void setUp() {
        commands = mock(EdgeSyncCommandService.class);
        queries = mock(EdgeSyncQueryService.class);
        stateMvc = MockMvcBuilders.standaloneSetup(new EdgeSyncStateController(commands))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        viewMvc = MockMvcBuilders.standaloneSetup(new InstallationEdgeSyncController(queries))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    // --- PUT /api/v1/edge/sync-state ---

    @Test
    void storesTheConfirmationOfTheCallingEdgeAndAnswersWithTheStoredState() throws Exception {
        when(commands.handle(new AcknowledgeEdgeVersionCommand(7L, 10L))).thenReturn(new AppliedEdgeVersion(7L, 12L, ACKED));

        stateMvc.perform(put(SYNC).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content("{\"appliedVersion\":10}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                // The stored version may be higher than the one reported.
                .andExpect(jsonPath("$.appliedVersion").value(12))
                .andExpect(jsonPath("$.ackedAt").value("2026-11-05T12:00:00Z"));
    }

    @Test
    void theEdgeIsTheCallerAndNeverSomethingInTheBody() throws Exception {
        when(commands.handle(any(AcknowledgeEdgeVersionCommand.class))).thenReturn(new AppliedEdgeVersion(7L, 1L, ACKED));

        stateMvc.perform(put(SYNC).principal(EDGE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appliedVersion\":1,\"edgeId\":99}"))
                .andExpect(status().isOk());

        verify(commands).handle(new AcknowledgeEdgeVersionCommand(7L, 1L));
    }

    @Test
    void answers404WhenTheEdgeHasNoActiveInstallationAnd422WhenTheVersionDoesNotExistYet() throws Exception {
        doThrow(new EdgeWithoutInstallation(7L)).when(commands).handle(new AcknowledgeEdgeVersionCommand(7L, 1L));
        stateMvc.perform(put(SYNC).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content("{\"appliedVersion\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("edge_without_installation"));

        doThrow(new AppliedVersionAhead(7L, 20L, 12L)).when(commands).handle(new AcknowledgeEdgeVersionCommand(7L, 20L));
        stateMvc.perform(put(SYNC).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content("{\"appliedVersion\":20}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("applied_version_ahead"));
    }

    @Test
    void rejectsAMissingNegativeOrNonNumericVersionWithoutCallingTheService() throws Exception {
        for (var body : new String[]{"{}", "{\"appliedVersion\":null}", "{\"appliedVersion\":-1}"}) {
            stateMvc.perform(put(SYNC).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation_failed"));
        }
        stateMvc.perform(put(SYNC).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content("{\"appliedVersion\":\"abc\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commands);
    }

    @Test
    void aCallerThatIsNotAnEdgeConfirmsNothing() throws Exception {
        stateMvc.perform(put(SYNC).principal(new TestingAuthenticationToken("42", null, "ROLE_ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"appliedVersion\":1}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(commands);
    }

    // --- GET /api/v1/installations/{id}/edge-sync ---

    @Test
    void showsTheAdministratorHowFarBehindEachEdgeIs() throws Exception {
        var seen = LocalDateTime.of(2026, 11, 5, 12, 5);
        when(queries.handle(new GetInstallationEdgeSyncQuery(3L))).thenReturn(Optional.of(new InstallationEdgeSync(3L, 12L, List.of(
                InstallationEdgeSync.EdgeSync.of(1L, EdgeType.MICROPHONE_EDGE, 12L, 12L, ACKED, seen),
                InstallationEdgeSync.EdgeSync.of(2L, EdgeType.ACTUATOR_EDGE, 12L, null, null, null)))));

        viewMvc.perform(get("/api/v1/installations/3/edge-sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installationId").value(3))
                .andExpect(jsonPath("$.currentVersion").value(12))
                .andExpect(jsonPath("$.edges[0].edgeType").value("MICROPHONE_EDGE"))
                .andExpect(jsonPath("$.edges[0].appliedVersion").value(12))
                .andExpect(jsonPath("$.edges[0].behindBy").value(0))
                .andExpect(jsonPath("$.edges[0].upToDate").value(true))
                .andExpect(jsonPath("$.edges[0].ackedAt").value("2026-11-05T12:00:00Z"))
                .andExpect(jsonPath("$.edges[0].lastSeenAt").value("2026-11-05T12:05:00Z"))
                .andExpect(jsonPath("$.edges[1].edgeType").value("ACTUATOR_EDGE"))
                .andExpect(jsonPath("$.edges[1].appliedVersion").doesNotExist())
                .andExpect(jsonPath("$.edges[1].behindBy").doesNotExist())
                .andExpect(jsonPath("$.edges[1].upToDate").value(false));
    }

    @Test
    void answers404ForAnUnknownInstallation() throws Exception {
        when(queries.handle(new GetInstallationEdgeSyncQuery(9L))).thenReturn(Optional.empty());

        viewMvc.perform(get("/api/v1/installations/9/edge-sync"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("installation_not_found"));
    }
}
