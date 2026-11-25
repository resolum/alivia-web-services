package com.resolum.alivia.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.tracking.application.internal.commandservices.ReportDeviceStatesCommandServiceImpl;
import com.resolum.alivia.tracking.application.internal.commandservices.TelemetrySampleRecorder;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService.ReportingEdge;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceState;
import com.resolum.alivia.tracking.domain.model.aggregates.EdgeStatus;
import com.resolum.alivia.tracking.domain.model.aggregates.TelemetrySample;
import com.resolum.alivia.tracking.domain.model.valueobjects.ConnectionStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.EdgeLink;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventInsertion;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventPage;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.TelemetryPage;
import com.resolum.alivia.tracking.domain.model.valueobjects.OperationalStatus;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.domain.repositories.DeviceStateRepository;
import com.resolum.alivia.tracking.domain.repositories.EdgeStatusRepository;
import com.resolum.alivia.tracking.domain.repositories.TelemetrySampleRepository;
import com.resolum.alivia.tracking.interfaces.rest.controllers.EdgeDeviceStatesController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The report of device states against the contract: the real controller and service over in-memory stores that behave
 * like the Mongo ones, with the examples of {@code docs/fixtures/tracking} as the request and the expected answer.
 */
class EdgeDeviceStatesContractTest {

    private static final String PATH = "/api/v1/edge/device-states";
    private static final Instant NOW = Instant.parse("2026-11-24T15:05:00Z");
    private static final UsernamePasswordAuthenticationToken EDGE =
            new UsernamePasswordAuthenticationToken("edge:3", null, List.of());
    private static final ObjectMapper JSON = new ObjectMapper();

    private static class InMemoryStates implements DeviceStateRepository {
        final Map<Long, DeviceState> kept = new HashMap<>();
        boolean failing;

        @Override
        public Optional<DeviceState> findByDeviceId(Long deviceId) {
            if (failing) throw new DataAccessResourceFailureException("MongoDB is down");
            return Optional.ofNullable(kept.get(deviceId));
        }

        @Override
        public List<DeviceState> findAllByDeviceIds(Collection<Long> deviceIds) {
            return deviceIds.stream().map(kept::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public boolean saveUnlessOlder(DeviceState state) {
            if (failing) throw new DataAccessResourceFailureException("MongoDB is down");
            var current = kept.get(state.deviceId());
            if (current != null && current.observedAt().isAfter(state.observedAt())) return false;
            kept.put(state.deviceId(), state);
            return true;
        }
    }

    private static final class InMemoryEdgeStatuses implements EdgeStatusRepository {
        EdgeStatus kept;
        boolean failing;

        @Override
        public Optional<EdgeStatus> findByEdgeId(Long edgeId) {
            return Optional.ofNullable(kept);
        }

        @Override
        public Optional<EdgeStatus> recordHeartbeat(EdgeStatus status) {
            if (failing) throw new DataAccessResourceFailureException("MongoDB is down");
            var before = Optional.ofNullable(kept);
            kept = status;
            return before;
        }

        @Override
        public List<EdgeStatus> findAllByHouseholdId(Long householdId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markOffline(Long edgeId, Instant seen, Instant offlineSince) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EdgeStatus> findOnlineSilentSince(Instant limit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryEvents implements DeviceEventRepository {
        final Map<String, DeviceEvent> kept = new HashMap<>();
        boolean failing;

        @Override
        public EventInsertion insert(DeviceEvent event) {
            if (failing) throw new DataAccessResourceFailureException("MongoDB is down");
            return kept.putIfAbsent(event.edgeId() + "/" + event.eventId(), event) == null
                    ? EventInsertion.INSERTED : EventInsertion.DUPLICATE;
        }

        @Override
        public Optional<DeviceEvent> findByEdgeAndEventId(Long edgeId, String eventId) {
            return Optional.ofNullable(kept.get(edgeId + "/" + eventId));
        }

        @Override
        public EventPage findByDevice(Long householdId, Long deviceId, Instant from, Instant to, EventType type, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DeviceEvent> findRecentByHousehold(Long householdId, int limit) {
            throw new UnsupportedOperationException();
        }

        List<EventType> types() {
            return kept.values().stream().map(DeviceEvent::type).toList();
        }
    }

    private static final class InMemorySamples implements TelemetrySampleRepository {
        final List<TelemetrySample> kept = new ArrayList<>();

        @Override
        public void save(TelemetrySample sample) {
            kept.add(sample);
        }

        @Override
        public TelemetryPage findByDevice(Long householdId, Long deviceId, Instant from, Instant to, int page, int size) {
            throw new UnsupportedOperationException();
        }
    }

    private final InMemoryStates states = new InMemoryStates();
    private final InMemoryEdgeStatuses edgeStatuses = new InMemoryEdgeStatuses();
    private final InMemoryEvents events = new InMemoryEvents();
    private final InMemorySamples samples = new InMemorySamples();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ExternalAssetsService assets = org.mockito.Mockito.mock(ExternalAssetsService.class);
    private final ExternalNotificationsService notifications = org.mockito.Mockito.mock(ExternalNotificationsService.class);
    private MockMvc mvc;

    private MockMvc mvcWith(java.util.concurrent.Executor sampleExecutor) {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var recorder = new TelemetrySampleRecorder(samples, clock, Duration.ofMinutes(5), null, meters, sampleExecutor);
        var service = new ReportDeviceStatesCommandServiceImpl(assets, states, edgeStatuses, events, recorder,
                notifications, TrackingPolicy.defaults(), clock, meters);
        return MockMvcBuilders.standaloneSetup(new EdgeDeviceStatesController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(assets.fetchReportingEdge(3L)).thenReturn(Optional.of(new ReportingEdge(
                new ReportingContext(3L, 30L, 300L, Set.of(10L, 11L, 12L, 13L)), "ACTUATOR", Map.of())));
        org.mockito.Mockito.when(assets.fetchReportingEdge(4L)).thenReturn(Optional.empty());
        mvc = mvcWith(Runnable::run);
    }

    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("docs", "fixtures", "tracking", name));
    }

    private String report(String body, int expectedStatus) throws Exception {
        return mvc.perform(put(PATH).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private static String device(long id, String connection, String operational, String state, String observedAt) {
        return "{\"deviceId\":" + id + ",\"connectionStatus\":\"" + connection + "\",\"operationalStatus\":\""
                + operational + "\",\"currentState\":\"" + state + "\",\"observedAt\":\"" + observedAt + "\"}";
    }

    private static String reportOf(String... devices) {
        return "{\"edge\":{\"pendingEvents\":0},\"devices\":[" + String.join(",", devices) + "]}";
    }

    private void kept(long deviceId, ConnectionStatus connection, OperationalStatus operational, String state, String observedAt) {
        var at = Instant.parse(observedAt);
        states.kept.put(deviceId, new DeviceState(deviceId, 3L, 300L, connection, operational, state, null, at, at, at,
                List.of()));
    }

    @Test
    void theReportOfTheContractIsAnsweredAsTheContractSays() throws Exception {
        // Device 11 had already been observed at 15:00, later than the 14:00 of the example.
        kept(11L, ConnectionStatus.CONNECTED, OperationalStatus.OK, "open", "2026-11-24T15:00:00Z");

        var answer = report(fixture("e3-device-states.request.json"), 200);

        assertThat(JSON.readTree(answer)).isEqualTo(JSON.readTree(fixture("e3-device-states.response.json")));
        assertThat(states.kept.get(10L).currentState()).isEqualTo("closed");
        assertThat(states.kept.get(10L).batteryPct()).isEqualTo(78);
        assertThat(states.kept.get(11L).observedAt()).isEqualTo(Instant.parse("2026-11-24T15:00:00Z"));
        assertThat(states.kept).doesNotContainKeys(12L, 99L);
        assertThat(edgeStatuses.kept.link()).isEqualTo(EdgeLink.ONLINE);
        assertThat(edgeStatuses.kept.pendingEvents()).isEqualTo(3);
        assertThat(edgeStatuses.kept.lastReportAt()).isEqualTo(NOW);
        // The first report of a device adds nothing to the history.
        assertThat(events.kept).isEmpty();
    }

    @Test
    void aReportWithoutDevicesIsOnlyTheHeartbeat() throws Exception {
        var answer = report(fixture("e3-heartbeat-only.request.json"), 200);

        assertThat(JSON.readTree(answer).get("results")).isEmpty();
        assertThat(edgeStatuses.kept.lastReportAt()).isEqualTo(NOW);
        assertThat(states.kept).isEmpty();
        report("{}", 200);
    }

    @Test
    void theDiagnosticsAreKeptAtMostOncePerInterval() throws Exception {
        var request = fixture("e3-device-states.request.json");

        report(request, 200);
        report(request, 200);

        assertThat(samples.kept).hasSize(1);
        assertThat(samples.kept.get(0).deviceId()).isEqualTo(10L);
    }

    @Test
    void aFullSampleQueueDropsTheSampleAndTheReportIsAnsweredAnyway() throws Exception {
        mvc = mvcWith(task -> {
            throw new RejectedExecutionException("full");
        });

        var answer = report(fixture("e3-device-states.request.json"), 200);

        assertThat(JSON.readTree(answer).get("results").get(0).get("status").asText()).isEqualTo("APPLIED");
        assertThat(samples.kept).isEmpty();
        assertThat(meters.get("tracking.samples.dropped").tag("cause", "queue_full").counter().count()).isEqualTo(1.0);
        assertThat(states.kept).containsKey(10L);
    }

    @Test
    void aDeviceThatDisconnectsAndComesBackLeavesTheirEventsOnce() throws Exception {
        kept(10L, ConnectionStatus.CONNECTED, OperationalStatus.OK, "closed", "2026-11-24T15:00:00Z");
        var down = reportOf(device(10, "DISCONNECTED", "OK", "closed", "2026-11-24T15:01:00Z"));
        var up = reportOf(device(10, "CONNECTED", "OK", "closed", "2026-11-24T15:02:00Z"));

        report(down, 200);
        report(down, 200);
        report(up, 200);
        report(up, 200);

        assertThat(events.types()).containsExactlyInAnyOrder(EventType.DEVICE_DISCONNECTED, EventType.DEVICE_RECOVERED);
        assertThat(states.kept.get(10L).connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
    }

    @Test
    void aFailureAndAChangeOfWhatItDoesAreBothInTheHistory() throws Exception {
        kept(10L, ConnectionStatus.CONNECTED, OperationalStatus.OK, "closed", "2026-11-24T15:00:00Z");
        kept(11L, ConnectionStatus.CONNECTED, OperationalStatus.OK, "off", "2026-11-24T15:00:00Z");

        report(reportOf(device(10, "CONNECTED", "FAILED", "closed", "2026-11-24T15:01:00Z"),
                device(11, "CONNECTED", "OK", "on", "2026-11-24T15:01:00Z")), 200);

        assertThat(events.types()).containsExactlyInAnyOrder(EventType.DEVICE_FAILURE, EventType.DEVICE_STATE_CHANGED);
    }

    @Test
    void aLateReportAddsNothingToTheHistoryAndKeepsTheState() throws Exception {
        kept(10L, ConnectionStatus.CONNECTED, OperationalStatus.OK, "closed", "2026-11-24T15:03:00Z");

        var answer = report(reportOf(device(10, "DISCONNECTED", "FAILED", "open", "2026-11-24T15:01:00Z")), 200);

        assertThat(JSON.readTree(answer).get("results").get(0).get("status").asText()).isEqualTo("STALE");
        assertThat(states.kept.get(10L).connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(events.kept).isEmpty();
    }

    @Test
    void whenTheEventsCannotBeKeptTheStateIsNotChangedAndTheRetryProducesThem() throws Exception {
        kept(10L, ConnectionStatus.CONNECTED, OperationalStatus.OK, "closed", "2026-11-24T15:00:00Z");
        var down = reportOf(device(10, "DISCONNECTED", "OK", "closed", "2026-11-24T15:01:00Z"));
        events.failing = true;

        mvc.perform(put(PATH).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content(down))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("tracking_storage_unavailable"));
        // What the state says is still the old one, so the retry sees the change.
        assertThat(states.kept.get(10L).connectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);

        events.failing = false;
        report(down, 200);

        assertThat(events.types()).containsExactly(EventType.DEVICE_DISCONNECTED);
        assertThat(states.kept.get(10L).connectionStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
    }

    @Test
    void whenTheStateCannotBeKeptAfterItsEventsTheRetryDoesNotRepeatThem() throws Exception {
        kept(10L, ConnectionStatus.CONNECTED, OperationalStatus.OK, "closed", "2026-11-24T15:00:00Z");
        var down = reportOf(device(10, "DISCONNECTED", "OK", "closed", "2026-11-24T15:01:00Z"));
        var failOnSave = new InMemoryStates() {
            int saves;

            @Override
            public boolean saveUnlessOlder(DeviceState state) {
                if (saves++ == 0) throw new DataAccessResourceFailureException("MongoDB is down");
                return super.saveUnlessOlder(state);
            }
        };
        failOnSave.kept.putAll(states.kept);
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var service = new ReportDeviceStatesCommandServiceImpl(assets, failOnSave, edgeStatuses, events,
                new TelemetrySampleRecorder(samples, clock, Duration.ofMinutes(5), null, meters, Runnable::run),
                notifications, TrackingPolicy.defaults(), clock, meters);
        mvc = MockMvcBuilders.standaloneSetup(new EdgeDeviceStatesController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        report(down, 503);
        report(down, 200);

        assertThat(events.types()).containsExactly(EventType.DEVICE_DISCONNECTED);
        assertThat(failOnSave.kept.get(10L).connectionStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
    }

    @Test
    void anEdgeThatWasOfflineReconnectsOnceAndTheOutageEnds() throws Exception {
        var silent = Instant.parse("2026-11-24T14:00:00Z");
        edgeStatuses.kept = new EdgeStatus(3L, 30L, 300L, silent.minusSeconds(120), EdgeLink.OFFLINE, 0, silent);

        report("{}", 200);
        edgeStatuses.kept = new EdgeStatus(3L, 30L, 300L, silent.minusSeconds(120), EdgeLink.OFFLINE, 0, silent);
        report("{}", 200);

        assertThat(events.types()).containsExactly(EventType.EDGE_RECONNECTED);
        assertThat(edgeStatuses.kept.link()).isEqualTo(EdgeLink.ONLINE);
        assertThat(edgeStatuses.kept.offlineSince()).isNull();
    }

    @Test
    void theRelativesAreToldTheEdgeIsBackWithTheSourceOfTheEventSoTheyAreToldOnce() throws Exception {
        org.mockito.Mockito.when(notifications.relativesOf(300L)).thenReturn(Set.of(8L));
        var silent = Instant.parse("2026-11-24T14:00:00Z");
        edgeStatuses.kept = new EdgeStatus(3L, 30L, 300L, silent.minusSeconds(120), EdgeLink.OFFLINE, 0, silent);

        report("{}", 200);

        var source = "rc-edge-3-" + silent.toEpochMilli();
        org.mockito.Mockito.verify(notifications).notifyOnce(8L, "EDGE_RECONNECTED", source, "Casa conectada de nuevo",
                "El equipo de la casa volvió a reportar.");
        assertThat(events.kept.values().iterator().next().eventId()).isEqualTo(source);
    }

    @Test
    void ifTheRelativesCannotBeToldTheReportIsAnsweredAnyway() throws Exception {
        org.mockito.Mockito.when(notifications.relativesOf(300L)).thenThrow(new IllegalStateException("notifications are down"));
        var silent = Instant.parse("2026-11-24T14:00:00Z");
        edgeStatuses.kept = new EdgeStatus(3L, 30L, 300L, silent.minusSeconds(120), EdgeLink.OFFLINE, 0, silent);

        report("{}", 200);

        assertThat(edgeStatuses.kept.link()).isEqualTo(EdgeLink.ONLINE);
        assertThat(events.types()).containsExactly(EventType.EDGE_RECONNECTED);
        assertThat(meters.get("tracking.link.failures").tag("stage", "reconnection_notice").counter().count()).isEqualTo(1.0);
    }

    @Test
    void anEdgeThatIsOnlineNeverNotifiesAReconnection() throws Exception {
        report("{}", 200);

        org.mockito.Mockito.verifyNoInteractions(notifications);
    }

    @Test
    void anEdgeThatIsOnlineDoesNotLeaveAReconnection() throws Exception {
        report("{}", 200);
        report("{}", 200);

        assertThat(events.kept).isEmpty();
    }

    @Test
    void whenTheHeartbeatCannotBeKeptTheEdgeIsToldToSendItAgain() throws Exception {
        edgeStatuses.failing = true;

        mvc.perform(put(PATH).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"));
    }

    @Test
    void aReportWithMoreDevicesThanAllowedIsRefusedWhole() throws Exception {
        var devices = new String[101];
        for (int i = 0; i < devices.length; i++) devices[i] = device(10, "CONNECTED", "OK", "closed", "2026-11-24T15:01:00Z");

        mvc.perform(put(PATH).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content(reportOf(devices)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("batch_too_large"));
        assertThat(states.kept).isEmpty();
        assertThat(edgeStatuses.kept).isNull();
    }

    @Test
    void anInvalidDeviceDoesNotSpoilTheOthersAndKeepsItsReliableState() throws Exception {
        kept(11L, ConnectionStatus.CONNECTED, OperationalStatus.OK, "open", "2026-11-24T15:00:00Z");

        var answer = report(reportOf(
                device(11, "BROKEN", "OK", "closed", "2026-11-24T15:01:00Z"),
                device(10, "CONNECTED", "OK", "closed", "2026-11-24T15:01:00Z"),
                device(13, "CONNECTED", "OK", "closed", "2030-01-01T00:00:00Z")), 200);

        var results = JSON.readTree(answer).get("results");
        assertThat(results.get(0).get("status").asText()).isEqualTo("REJECTED");
        assertThat(results.get(0).get("reason").asText()).isEqualTo("invalid_status");
        assertThat(results.get(1).get("status").asText()).isEqualTo("APPLIED");
        assertThat(results.get(2).get("status").asText()).isEqualTo("REJECTED");
        assertThat(states.kept.get(11L).currentState()).isEqualTo("open");
        assertThat(states.kept).containsKey(10L).doesNotContainKey(13L);
        // A null reason is not in the answer.
        JsonNode applied = results.get(1);
        assertThat(applied.has("reason")).isFalse();
    }

    @Test
    void aBrokenBodyIsRefused() throws Exception {
        for (var body : new String[]{"{\"devices\":", "[]", "{\"devices\":[null]}"}) {
            mvc.perform(put(PATH).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(edgeStatuses.kept).isNull();
    }

    @Test
    void whoIsNotAnEdgeOrHasNoHomeIsRefusedAndNothingIsKept() throws Exception {
        mvc.perform(put(PATH).principal(new UsernamePasswordAuthenticationToken("7", null, List.of()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(PATH).principal(new UsernamePasswordAuthenticationToken("edge:4", null, List.of()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("edge_without_installation"));
        assertThat(edgeStatuses.kept).isNull();
        assertThat(states.kept).isEmpty();
    }
}
