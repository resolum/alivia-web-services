package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeTableVersionByEdgeIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.model.valueobjects.NodeRef;
import com.resolum.alivia.assets.domain.model.commands.RecordEdgeSeenCommand;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeTableVersion;
import com.resolum.alivia.assets.domain.services.EdgeSyncCommandService;
import com.resolum.alivia.assets.domain.services.EdgeTableWaitService;
import com.resolum.alivia.assets.domain.exceptions.EdgeWithoutInstallation;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.springframework.test.web.servlet.MvcResult;
import com.resolum.alivia.assets.domain.services.HouseholdDeviceRegistryQueryService;
import com.resolum.alivia.assets.interfaces.rest.controllers.EdgeDeviceTableController;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What an edge service gets when it asks for the table of devices of its household. */
class EdgeDeviceTableControllerTest {

    private static final String PATH = "/api/v1/edge/device-table";
    private static final Authentication EDGE = new TestingAuthenticationToken("edge:7", null, "ROLE_EDGE_MICROPHONE");
    private static final EdgeAccessTable TABLE = new EdgeAccessTable(3L, 5L, 12L, List.of(
            new NodeRef(1L, "AA:BB:CC:DD:EE:01", DeviceType.LIGHT, AssetStatus.IN_USE, "sala", "Luz principal"),
            new NodeRef(2L, "AA:BB:CC:DD:EE:02", DeviceType.MICROPHONE, AssetStatus.MAINTENANCE, "sala", "Micrófono")));

    private HouseholdDeviceRegistryQueryService queries;
    private EdgeSyncCommandService sync;
    private EdgeTableWaitService waits;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        queries = mock(HouseholdDeviceRegistryQueryService.class);
        sync = mock(EdgeSyncCommandService.class);
        waits = mock(EdgeTableWaitService.class);
        mvc = MockMvcBuilders.standaloneSetup(new EdgeDeviceTableController(queries, sync, waits))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(queries.handle(new GetEdgeTableVersionByEdgeIdQuery(7L))).thenReturn(Optional.of(new EdgeTableVersion(3L, 12L)));
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L))).thenReturn(Optional.of(TABLE));
    }

    @Test
    void answersWithTheCompleteTableAndItsVersionAsEtag() throws Exception {
        mvc.perform(get(PATH).principal(EDGE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3.12\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.installationId").value(3))
                .andExpect(jsonPath("$.householdId").value(5))
                .andExpect(jsonPath("$.version").value(12))
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.nodes[0].deviceId").value(1))
                .andExpect(jsonPath("$.nodes[0].macAddress").value("AA:BB:CC:DD:EE:01"))
                .andExpect(jsonPath("$.nodes[0].deviceType").value("LIGHT"))
                .andExpect(jsonPath("$.nodes[0].status").value("IN_USE"))
                .andExpect(jsonPath("$.nodes[0].roomKey").value("sala"))
                .andExpect(jsonPath("$.nodes[0].function").value("Luz principal"))
                .andExpect(jsonPath("$.nodes[1].status").value("MAINTENANCE"));
    }

    @Test
    void anEmptyTableIsStillATable() throws Exception {
        when(queries.handle(new GetEdgeTableVersionByEdgeIdQuery(7L))).thenReturn(Optional.of(new EdgeTableVersion(3L, 0L)));
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L)))
                .thenReturn(Optional.of(new EdgeAccessTable(3L, 5L, 0L, List.of())));

        mvc.perform(get(PATH).principal(EDGE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3.0\""))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.nodes.length()").value(0));
    }

    @Test
    void anEdgeThatIsUpToDateGetsAnEmpty304WithoutReadingTheTable() throws Exception {
        mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"3.12\""))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3.12\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        verify(queries, never()).handle(any(GetEdgeAccessTableByEdgeIdQuery.class));
    }

    @Test
    void theVersionMayComeAsAWeakTagInAListOrAsAStar() throws Exception {
        for (var header : new String[]{"W/\"3.12\"", "\"3.3\", \"3.12\"", " \"3.3\" ,W/\"3.12\" ", "*"}) {
            mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, header))
                    .andExpect(status().isNotModified());
        }
    }

    @Test
    void anOlderOrNewerOrMalformedVersionGetsTheTable() throws Exception {
        for (var header : new String[]{"\"3.11\"", "12", "\"\"", "garbage", "\"12", "", "  ", "\"3.012\"", "\"1,2\""}) {
            mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, header))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(12));
        }
    }

    @Test
    void theEtagIsTheVersionOfTheTableThatWasReturnedEvenIfItIsNewerThanTheOneChecked() throws Exception {
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L)))
                .thenReturn(Optional.of(new EdgeAccessTable(3L, 5L, 13L, List.of())));

        mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"3.11\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3.13\""))
                .andExpect(jsonPath("$.version").value(13));
    }

    @Test
    void anEdgeWithoutAnActiveInstallationGets404() throws Exception {
        when(queries.handle(new GetEdgeTableVersionByEdgeIdQuery(7L))).thenReturn(Optional.empty());

        mvc.perform(get(PATH).principal(EDGE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("edge_without_installation"));

        verify(queries, never()).handle(any(GetEdgeAccessTableByEdgeIdQuery.class));
    }

    @Test
    void anEdgeRemovedBetweenBothReadsGets404() throws Exception {
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(7L))).thenReturn(Optional.empty());

        mvc.perform(get(PATH).principal(EDGE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("edge_without_installation"));
    }

    @Test
    void theTableIsAlwaysTheOneOfTheCallingEdge() throws Exception {
        var other = new TestingAuthenticationToken("edge:9", null, "ROLE_EDGE_ACTUATOR");
        when(queries.handle(new GetEdgeTableVersionByEdgeIdQuery(9L))).thenReturn(Optional.of(new EdgeTableVersion(8L, 4L)));
        when(queries.handle(new GetEdgeAccessTableByEdgeIdQuery(9L)))
                .thenReturn(Optional.of(new EdgeAccessTable(8L, 6L, 4L, List.of())));

        mvc.perform(get(PATH).principal(other)).andExpect(jsonPath("$.installationId").value(8));
        mvc.perform(get(PATH).principal(EDGE)).andExpect(jsonPath("$.installationId").value(3));
    }

    @Test
    void aCallerThatIsNotNamedAsAnEdgeGetsNothing() throws Exception {
        for (var name : new String[]{"42", "edge:", "edge:abc", "edge:0", "edge:-3", "Edge:7"}) {
            mvc.perform(get(PATH).principal(new TestingAuthenticationToken(name, null, "ROLE_EDGE_MICROPHONE")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(PATH)).andExpect(status().isForbidden());

        verifyNoInteractions(queries);
    }

    // --- the edge was heard ---

    @Test
    void everyAskIsRecordedWhetherTheEdgeGetsTheTableOrA304() throws Exception {
        mvc.perform(get(PATH).principal(EDGE)).andExpect(status().isOk());
        mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"3.12\"")).andExpect(status().isNotModified());

        verify(sync, times(2)).handle(new RecordEdgeSeenCommand(7L, 3L));
    }

    @Test
    void anEdgeWithoutInstallationIsNotRecorded() throws Exception {
        when(queries.handle(new GetEdgeTableVersionByEdgeIdQuery(7L))).thenReturn(Optional.empty());

        mvc.perform(get(PATH).principal(EDGE)).andExpect(status().isNotFound());

        verifyNoInteractions(sync);
    }

    @Test
    void failingToRecordThatTheEdgeAskedNeverKeepsItFromItsTable() throws Exception {
        doThrow(new IllegalStateException("database busy")).when(sync).handle(any(RecordEdgeSeenCommand.class));

        mvc.perform(get(PATH).principal(EDGE)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(12));
        mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"3.12\"")).andExpect(status().isNotModified());
    }

    // --- ?wait=n: the request waits for a newer table ---

    private static final EdgeAccessTable NEWER = new EdgeAccessTable(3L, 5L, 13L, List.of(
            new NodeRef(1L, "AA:BB:CC:DD:EE:01", DeviceType.LIGHT, AssetStatus.IN_USE, "sala", "Luz principal")));

    private MvcResult waiting(String ifNoneMatch, String wait) throws Exception {
        var request = get(PATH).param("wait", wait).principal(EDGE);
        if (ifNoneMatch != null) {
            request = request.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        }
        return mvc.perform(request).andExpect(request().asyncStarted()).andReturn();
    }

    @Test
    void anEdgeThatIsUpToDateWaitsAndGetsTheNewTableAsSoonAsItExists() throws Exception {
        var pending = new CompletableFuture<Optional<EdgeAccessTable>>();
        when(waits.awaitNewerTable(7L, 3L, 12L, Duration.ofSeconds(20))).thenReturn(pending);

        var result = waiting("\"3.12\"", "20");
        // Nothing is answered while there is no newer table, and the table was not read.
        verify(queries, never()).handle(any(GetEdgeAccessTableByEdgeIdQuery.class));
        pending.complete(Optional.of(NEWER));

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3.13\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.version").value(13))
                .andExpect(jsonPath("$.nodes.length()").value(1));
    }

    @Test
    void whenTheTimeRunsOutTheAnswerIsThe304OfTheVersionTheEdgeHas() throws Exception {
        var pending = new CompletableFuture<Optional<EdgeAccessTable>>();
        when(waits.awaitNewerTable(eq(7L), eq(3L), eq(12L), any())).thenReturn(pending);

        var result = waiting("\"3.12\"", "25");
        pending.complete(Optional.empty());

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3.12\""))
                .andExpect(content().string(""));
    }

    @Test
    void anEdgeThatLostItsInstallationWhileWaitingGets404() throws Exception {
        var pending = new CompletableFuture<Optional<EdgeAccessTable>>();
        when(waits.awaitNewerTable(eq(7L), eq(3L), eq(12L), any())).thenReturn(pending);

        var result = waiting("\"3.12\"", "25");
        pending.completeExceptionally(new EdgeWithoutInstallation(7L));

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("edge_without_installation"));
    }

    @Test
    void anEdgeThatIsBehindIsAnsweredAtOnceWithoutWaiting() throws Exception {
        for (var header : new String[]{"\"3.11\"", "garbage", null}) {
            var result = waiting(header, "25");
            mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(12));
        }

        verifyNoInteractions(waits);
    }

    @Test
    void waitingZeroSecondsIsTheOrdinary304() throws Exception {
        var result = waiting("\"3.12\"", "0");

        mvc.perform(asyncDispatch(result)).andExpect(status().isNotModified()).andExpect(header().string(HttpHeaders.ETAG, "\"3.12\""));
        verifyNoInteractions(waits);
    }

    @Test
    void theWaitIsCutAtTwentyFiveSeconds() throws Exception {
        when(waits.awaitNewerTable(eq(7L), eq(3L), eq(12L), any())).thenReturn(new CompletableFuture<>());

        waiting("\"3.12\"", "3600");

        verify(waits).awaitNewerTable(7L, 3L, 12L, Duration.ofSeconds(25));
    }

    @Test
    void aNegativeOrNonNumericWaitIsRejectedBeforeAnythingHappens() throws Exception {
        for (var wait : new String[]{"-1", "abc", "1.5", ""}) {
            mvc.perform(get(PATH).param("wait", wait).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"3.12\""))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(waits, sync);
    }

    @Test
    void aWaitingEdgeIsRecordedAsHeardOnceWhenItAsks() throws Exception {
        when(waits.awaitNewerTable(eq(7L), eq(3L), eq(12L), any())).thenReturn(new CompletableFuture<>());

        waiting("\"3.12\"", "25");

        verify(sync, times(1)).handle(new RecordEdgeSeenCommand(7L, 3L));
    }

    @Test
    void aWaitingCallThatIsNotAnEdgeOrHasNoInstallationFailsAtOnce() throws Exception {
        mvc.perform(get(PATH).param("wait", "25").principal(new TestingAuthenticationToken("42", null, "ROLE_EDGE_MICROPHONE")))
                .andExpect(status().isForbidden());

        when(queries.handle(new GetEdgeTableVersionByEdgeIdQuery(7L))).thenReturn(Optional.empty());
        mvc.perform(get(PATH).param("wait", "25").principal(EDGE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("edge_without_installation"));

        verifyNoInteractions(waits);
    }

    // --- the tag carries the installation (an edge that was moved must get the table of its new home) ---

    @Test
    void aTagOfAnotherInstallationNeverMatchesEvenAtTheSameVersion() throws Exception {
        for (var header : new String[]{"\"8.12\"", "W/\"8.12\"", "\"8.12\", \"9.12\""}) {
            mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, header))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ETAG, "\"3.12\""))
                    .andExpect(jsonPath("$.installationId").value(3));
        }
    }

    @Test
    void theOldTagThatHadOnlyTheVersionNeverMatches() throws Exception {
        mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"12\""))
                .andExpect(status().isOk());
        mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"3.012\""))
                .andExpect(status().isOk());
        mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"3.12.1\""))
                .andExpect(status().isOk());
        mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"-3.12\""))
                .andExpect(status().isOk());
    }

    // --- an edge that is ahead of the server (the database went back in time) ---

    @Test
    void anEdgeAheadOfTheServerIsToldSoAndNeverGetsTheOlderTableToIgnore() throws Exception {
        for (var header : new String[]{"\"3.13\"", "\"3.40\"", "W/\"3.99999\"", "\"8.12\", \"3.13\""}) {
            mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, header))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("table_version_ahead"))
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("without If-None-Match")));
        }

        verify(queries, never()).handle(any(GetEdgeAccessTableByEdgeIdQuery.class));
    }

    @Test
    void anEdgeAheadOfAnotherInstallationIsNotAheadOfThisOne() throws Exception {
        mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"8.40\""))
                .andExpect(status().isOk());
    }

    @Test
    void theExactTagWinsOverAnAheadOneInTheSameList() throws Exception {
        mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"3.40\", \"3.12\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void afterTheConflictTheEdgeAsksAgainWithoutATagAndGetsTheTableOfTheServer() throws Exception {
        mvc.perform(get(PATH).principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"3.40\""))
                .andExpect(status().isConflict());

        mvc.perform(get(PATH).principal(EDGE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3.12\""))
                .andExpect(jsonPath("$.version").value(12));
    }

    @Test
    void theWaitingVariantAlsoToldsAnAheadEdgeInsteadOfAnsweringImmediatelyForever() throws Exception {
        mvc.perform(get(PATH).param("wait", "25").principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"3.40\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("table_version_ahead"));

        verifyNoInteractions(waits);
        verify(sync, times(1)).handle(new RecordEdgeSeenCommand(7L, 3L));
    }

    @Test
    void theWaitingVariantGivesTheTableToAnEdgeFromAnotherInstallationAtOnce() throws Exception {
        var result = mvc.perform(get(PATH).param("wait", "25").principal(EDGE).header(HttpHeaders.IF_NONE_MATCH, "\"8.12\""))
                .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3.12\""));
        verifyNoInteractions(waits);
    }
}
