package com.resolum.alivia.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.shared.interfaces.rest.GlobalExceptionHandler;
import com.resolum.alivia.tracking.application.internal.commandservices.RecordEdgeEventsCommandServiceImpl;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService.ReportingEdge;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventInsertion;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventPage;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventOrigin;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import com.resolum.alivia.tracking.interfaces.rest.controllers.EdgeEventsBatchController;
import com.resolum.alivia.tracking.interfaces.rest.controllers.EdgeEventsController;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The batch endpoint against the contract: the real controller and service over an in-memory store that behaves like the
 * unique index of MongoDB, with the examples of {@code docs/fixtures/tracking} as the request and the expected answer.
 */
class EdgeEventsBatchContractTest {

    private static final String BATCH = "/api/v1/edge/events/batch";
    private static final Instant NOW = Instant.parse("2026-11-24T15:05:00Z");
    private static final UsernamePasswordAuthenticationToken EDGE =
            new UsernamePasswordAuthenticationToken("edge:3", null, List.of());
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Keeps the events like a unique (edge, event id) index: the second one with the same pair is a duplicate. */
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
            if (failing) throw new DataAccessResourceFailureException("MongoDB is down");
            return Optional.ofNullable(kept.get(edgeId + "/" + eventId));
        }

        @Override
        public EventPage findByDevice(Long householdId, Long deviceId, Instant from, Instant to, EventType type, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forEachCommandFact(Long householdId, Long deviceId, Instant from, Instant to,
                java.util.function.Consumer<com.resolum.alivia.tracking.domain.model.valueobjects.CommandFact> action) {
            throw new UnsupportedOperationException();
        }


        @Override
        public List<DeviceEvent> findRecentByHousehold(Long householdId, int limit) {
            throw new UnsupportedOperationException();
        }
    }

    private final InMemoryEvents store = new InMemoryEvents();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ExternalNotificationsService notifications = mock(ExternalNotificationsService.class);
    private final ExternalAssetsService assets = mock(ExternalAssetsService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(assets.fetchReportingEdge(3L)).thenReturn(Optional.of(new ReportingEdge(
                new ReportingContext(3L, 30L, 300L, Set.of(10L, 11L, 13L)), "ACTUATOR", Map.of(13L, "Luz de la sala"))));
        when(assets.fetchReportingEdge(4L)).thenReturn(Optional.empty());
        when(notifications.relativesOf(300L)).thenReturn(Set.of(8L));
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        var service = new RecordEdgeEventsCommandServiceImpl(assets, notifications, store, TrackingPolicy.defaults(),
                Clock.fixed(NOW, ZoneOffset.UTC), meters);
        mvc = MockMvcBuilders.standaloneSetup(new EdgeEventsBatchController(service), new EdgeEventsController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("docs", "fixtures", "tracking", name));
    }

    private String sync(String body, int expectedStatus) throws Exception {
        return mvc.perform(post(BATCH).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private static String batchOf(String... events) {
        return "{\"events\":[" + String.join(",", events) + "]}";
    }

    private static String urgent(String eventId, String type, String extra) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"" + type + "\",\"occurredAt\":\"2026-11-24T15:04:50Z\""
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    @Test
    void theBatchOfTheContractIsAnsweredAsTheContractSays() throws Exception {
        // The third event of the example had already been synchronized before.
        var alreadyKept = JSON.readTree(fixture("e2-batch.request.json")).get("events").get(2);
        sync(batchOf(alreadyKept.toString()), 200);
        store.kept.values().forEach(event -> assertThat(event.eventId()).isEqualTo("9f1c0e7a2b6d4c3e8a5b1d2f3c4e5a03"));

        var answer = sync(fixture("e2-batch.request.json"), 200);

        JsonNode expected = JSON.readTree(fixture("e2-batch.response.json"));
        assertThat(JSON.readTree(answer)).isEqualTo(expected);
        // What was kept: the five valid events plus the one before; the rejected one is not.
        assertThat(store.kept).hasSize(5);
        assertThat(store.findByEdgeAndEventId(3L, "9f1c0e7a2b6d4c3e8a5b1d2f3c4e5a06")).isEmpty();
    }

    @Test
    void anEventThatArrivesLateIsMarkedAsExecutedLocallyAndOneOfNowIsNot() throws Exception {
        sync(fixture("e2-batch.request.json"), 200);
        sync(batchOf("{\"eventId\":\"now\",\"type\":\"DEVICE_STATE_CHANGED\",\"occurredAt\":\"2026-11-24T15:04:30Z\",\"deviceId\":10}"), 200);

        // The low battery of the example is from 14:12 and the result from 100 s before: both come from the queue of the edge.
        assertThat(store.findByEdgeAndEventId(3L, "9f1c0e7a2b6d4c3e8a5b1d2f3c4e5a05").orElseThrow().origin())
                .isEqualTo(EventOrigin.SYNCED);
        assertThat(store.findByEdgeAndEventId(3L, "9f1c0e7a2b6d4c3e8a5b1d2f3c4e5a02").orElseThrow().origin())
                .isEqualTo(EventOrigin.SYNCED);
        // One that happened 30 s before arrives as it happens.
        assertThat(store.findByEdgeAndEventId(3L, "now").orElseThrow().origin()).isEqualTo(EventOrigin.LIVE);
    }

    @Test
    void sendingTheSameBatchAgainKeepsNothingNewAndNotifiesNobodyAgain() throws Exception {
        sync(fixture("e2-batch.request.json"), 200);
        int kept = store.kept.size();
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        var second = JSON.readTree(sync(fixture("e2-batch.request.json"), 200)).get("results");

        assertThat(store.kept).hasSize(kept);
        var statuses = new ArrayList<String>();
        second.forEach(result -> statuses.add(result.get("status").asText()));
        assertThat(statuses).containsExactly("DUPLICATE", "DUPLICATE", "DUPLICATE", "DUPLICATE", "DUPLICATE", "REJECTED");
    }

    @Test
    void anEventAlreadyReportedAsUrgentIsADuplicateInTheBatchAndNobodyIsToldTwice() throws Exception {
        mvc.perform(post("/api/v1/edge/events").principal(EDGE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"help-1\",\"type\":\"HELP_REQUESTED\",\"detail\":\"ayuda\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.notified").value(1));
        // Communications already has that source: it answers that the notification is not new.
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        var answer = sync(batchOf(urgent("help-1", "HELP_REQUESTED", "\"detail\":\"ayuda\"")), 200);

        assertThat(JSON.readTree(answer).get("results").get(0).get("status").asText()).isEqualTo("DUPLICATE");
        // Both reports use the same source key, which is what makes Communications keep one notification.
        verify(notifications, times(2)).notifyOnce(8L, "HELP_REQUESTED", "edge-help-1", "Solicitud de asistencia",
                "Tu familiar pidió ayuda desde casa: «ayuda».");
        assertThat(store.kept).hasSize(1);
    }

    @Test
    void urgentEventsOfABatchTellTheFamilyAndAreNeverRefusedForADeviceOrTheClock() throws Exception {
        var answer = sync(batchOf(
                urgent("h1", "HELP_REQUESTED", "\"deviceId\":99"),
                urgent("l1", "LOW_BATTERY", "\"deviceId\":13,\"batteryPct\":15"),
                "{\"eventId\":\"f1\",\"type\":\"DEVICE_FAILURE\",\"occurredAt\":\"2030-01-01T00:00:00Z\",\"detail\":\"sin respuesta\"}",
                urgent("l2", "LOW_BATTERY", "\"batteryPct\":101")), 200);

        var results = JSON.readTree(answer).get("results");
        assertThat(results.get(0).get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(results.get(1).get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(results.get(2).get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(results.get(3).get("status").asText()).isEqualTo("REJECTED");
        assertThat(results.get(3).get("reason").asText()).isEqualTo("battery_out_of_range");
        verify(notifications).notifyOnce(8L, "LOW_BATTERY", "edge-l1", "Batería baja", "Luz de la sala tiene 15% de batería.");
        verify(notifications).notifyOnce(8L, "ACTUATOR_FAILURE", "edge-f1", "Falla en un dispositivo",
                "Un dispositivo de la casa no respondió: sin respuesta.");
        assertThat(store.findByEdgeAndEventId(3L, "f1").orElseThrow().occurredAt()).isEqualTo(NOW);
        assertThat(store.findByEdgeAndEventId(3L, "h1").orElseThrow().deviceId()).isNull();
        assertThat(store.findByEdgeAndEventId(3L, "l1").orElseThrow().type()).isEqualTo(EventType.LOW_BATTERY);
    }

    @Test
    void anInvalidEventIsRejectedWithItsReasonAndTheOthersAreKept() throws Exception {
        var answer = sync(batchOf(
                "{\"eventId\":\"has space\",\"type\":\"COMMAND_RESULT\"}",
                "{\"type\":\"COMMAND_RESULT\"}",
                "{\"eventId\":\"a1\",\"type\":\"EDGE_DISCONNECTED\",\"occurredAt\":\"2026-11-24T15:04:00Z\"}",
                "{\"eventId\":\"a2\",\"type\":\"COMMAND_RESULT\",\"occurredAt\":\"2026-11-24T15:04:00Z\",\"deviceId\":10,"
                        + "\"commandId\":\"c\",\"action\":\"open\",\"result\":\"SUCCESS\",\"attributes\":{\"audio\":\"x\"}}",
                "{\"eventId\":\"ok\",\"type\":\"DEVICE_STATE_CHANGED\",\"occurredAt\":\"2026-11-24T15:04:00Z\",\"deviceId\":10}"), 200);

        var reasons = new ArrayList<String>();
        JSON.readTree(answer).get("results").forEach(r -> reasons.add(r.get("status").asText() + ":"
                + (r.has("reason") ? r.get("reason").asText() : "")));
        assertThat(reasons).containsExactly("REJECTED:invalid_event_id", "REJECTED:invalid_event_id",
                "REJECTED:type_not_allowed", "REJECTED:forbidden_attribute", "ACCEPTED:");
        assertThat(store.kept).hasSize(1);
    }

    @Test
    void theSameEventTwiceInOneBatchIsKeptOnce() throws Exception {
        var event = "{\"eventId\":\"dup\",\"type\":\"DEVICE_STATE_CHANGED\",\"occurredAt\":\"2026-11-24T15:04:00Z\",\"deviceId\":10}";

        var results = JSON.readTree(sync(batchOf(event, event), 200)).get("results");

        assertThat(results.get(0).get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(results.get(1).get("status").asText()).isEqualTo("DUPLICATE");
    }

    @Test
    void aBatchOverTheLimitIsRefusedWholeAndNothingIsKept() throws Exception {
        var events = new String[101];
        for (int i = 0; i < events.length; i++) {
            events[i] = "{\"eventId\":\"e" + i + "\",\"type\":\"DEVICE_STATE_CHANGED\",\"occurredAt\":\"2026-11-24T15:04:00Z\",\"deviceId\":10}";
        }

        mvc.perform(post(BATCH).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content(batchOf(events)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("batch_too_large"))
                .andExpect(jsonPath("$.detail").value(JSON.readTree(fixture("problem-batch-too-large.json")).get("detail").asText()));
        assertThat(store.kept).isEmpty();
        // 100 are accepted: the limit is inclusive.
        sync(batchOf(java.util.Arrays.copyOf(events, 100)), 200);
        assertThat(store.kept).hasSize(100);
    }

    @Test
    void anEmptyOrBrokenBodyIsRefused() throws Exception {
        for (var body : new String[]{"{\"events\":[]}", "{}", "{\"events\":[null]}", "{\"events\":", "[]"}) {
            mvc.perform(post(BATCH).principal(EDGE).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(store.kept).isEmpty();
    }

    @Test
    void whenTheEventsCannotBeKeptTheEdgeIsToldToSendThemAgainAndNothingIsAcknowledged() throws Exception {
        store.failing = true;

        mvc.perform(post(BATCH).principal(EDGE).contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("e2-batch.request.json")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("tracking_storage_unavailable"));

        // The edge sends the same batch again once the store is back: everything is accepted then.
        store.failing = false;
        var again = JSON.readTree(sync(fixture("e2-batch.request.json"), 200)).get("results");
        assertThat(again.get(0).get("status").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    void whoIsNotAnEdgeOrHasNoHomeIsRefused() throws Exception {
        mvc.perform(post(BATCH).principal(new UsernamePasswordAuthenticationToken("7", null, List.of()))
                        .contentType(MediaType.APPLICATION_JSON).content(fixture("e2-batch.request.json")))
                .andExpect(status().isForbidden());
        mvc.perform(post(BATCH).principal(new UsernamePasswordAuthenticationToken("edge:4", null, List.of()))
                        .contentType(MediaType.APPLICATION_JSON).content(fixture("e2-batch.request.json")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("edge_without_installation"));
        verify(notifications, never()).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void theSingleUrgentEndpointStillAnswersWhenTheStoreIsDown() throws Exception {
        store.failing = true;

        mvc.perform(post("/api/v1/edge/events").principal(EDGE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"help-2\",\"type\":\"HELP_REQUESTED\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.notified").value(1));
        verify(notifications).notifyOnce(8L, "HELP_REQUESTED", "edge-help-2", "Solicitud de asistencia",
                "Tu familiar pidió ayuda desde casa.");
    }

    private double events(String outcome, String via) {
        var counter = meters.find("tracking.events").tag("outcome", outcome).tag("via", via).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void everyEventOfABatchIsCountedByWhatBecameOfIt() throws Exception {
        sync(fixture("e2-batch.request.json"), 200);

        assertThat(events("accepted", "batch")).isEqualTo(5);
        assertThat(events("rejected", "batch")).isEqualTo(1);
        assertThat(events("duplicate", "batch")).isZero();

        sync(fixture("e2-batch.request.json"), 200);

        assertThat(events("accepted", "batch")).isEqualTo(5);
        assertThat(events("duplicate", "batch")).isEqualTo(5);
        assertThat(events("rejected", "batch")).isEqualTo(2);
    }

    @Test
    void theSingleUrgentEndpointCountsItsOwnAcceptedAndDuplicateEvents() throws Exception {
        var body = "{\"eventId\":\"help-9\",\"type\":\"HELP_REQUESTED\"}";

        mvc.perform(post("/api/v1/edge/events").principal(EDGE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/edge/events").principal(EDGE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        assertThat(events("accepted", "single")).isEqualTo(1);
        assertThat(events("duplicate", "single")).isEqualTo(1);
        assertThat(events("accepted", "batch")).isZero();
    }

    @Test
    void anEventThatCannotBeKeptIsAStorageFailureNotARefusal() throws Exception {
        store.failing = true;

        sync(batchOf(urgent("h1", "HELP_REQUESTED", "")), 503);

        assertThat(meters.get("tracking.storage.failures").tag("operation", "insert_event").counter().count()).isEqualTo(1.0);
        assertThat(events("accepted", "batch") + events("duplicate", "batch") + events("rejected", "batch")).isZero();
    }

    // ---- a request for help that arrives late (D-TR-8)

    private static String helpAt(String eventId, String occurredAt) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"HELP_REQUESTED\",\"occurredAt\":\"" + occurredAt
                + "\",\"detail\":\"ayuda\"}";
    }

    private static final String LATE_BODY = "Tu familiar pidió ayuda a las 07:12, pero el aviso llegó con retraso porque la casa estuvo sin conexión. Dijo: «ayuda».";

    @Test
    void aRequestForHelpThatArrivesHoursLateIsToldAsDelayedWithTheRealHour() throws Exception {
        sync(batchOf(helpAt("h-late", "2026-11-24T12:12:00Z")), 200);

        verify(notifications).notifyOnce(8L, "HELP_REQUEST_DELAYED", "edge-h-late", "Pedido de ayuda con retraso", LATE_BODY);
        verify(notifications, never()).notifyOnce(anyLong(), org.mockito.ArgumentMatchers.eq("HELP_REQUESTED"), anyString(),
                anyString(), anyString());
    }

    @Test
    void aRequestForHelpWithinTheDelayIsToldAsLiveAndTheLimitIsExact() throws Exception {
        sync(batchOf(helpAt("h-1", "2026-11-24T15:00:00Z"), helpAt("h-2", "2026-11-24T14:59:59Z")), 200);

        // Exactly five minutes late is still live; one second more is delayed.
        verify(notifications).notifyOnce(8L, "HELP_REQUESTED", "edge-h-1", "Solicitud de asistencia",
                "Tu familiar pidió ayuda desde casa: «ayuda».");
        verify(notifications).notifyOnce(org.mockito.ArgumentMatchers.eq(8L), org.mockito.ArgumentMatchers.eq("HELP_REQUEST_DELAYED"),
                org.mockito.ArgumentMatchers.eq("edge-h-2"), anyString(), anyString());
    }

    @Test
    void aDelayedRequestFromAnotherDayNamesTheDay() throws Exception {
        sync(batchOf(helpAt("h-old", "2026-11-23T14:00:00Z")), 200);

        verify(notifications).notifyOnce(8L, "HELP_REQUEST_DELAYED", "edge-h-old", "Pedido de ayuda con retraso",
                "Tu familiar pidió ayuda el 23/11 a las 09:00, pero el aviso llegó con retraso porque la casa estuvo sin conexión. Dijo: «ayuda».");
    }

    @Test
    void sendingTheDelayedRequestAgainDoesNotTellTheFamilyAgain() throws Exception {
        sync(batchOf(helpAt("h-late", "2026-11-24T12:12:00Z")), 200);

        var again = JSON.readTree(sync(batchOf(helpAt("h-late", "2026-11-24T12:12:00Z")), 200)).get("results").get(0);

        assertThat(again.get("status").asText()).isEqualTo("DUPLICATE");
        verify(notifications, times(1)).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aRequestTheFamilyAlreadyKnewAsLiveIsNotToldAgainWhenTheBatchBringsItLate() throws Exception {
        // First it arrives as it happens, by the single endpoint, and is kept.
        mvc.perform(post("/api/v1/edge/events").principal(EDGE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"h-live\",\"type\":\"HELP_REQUESTED\",\"occurredAt\":\"2026-11-24T14:59:50Z\"}"))
                .andExpect(status().isAccepted());
        // Then the edge, which never got the answer, sends it again hours later in a batch.
        org.mockito.Mockito.clearInvocations(notifications);

        var again = JSON.readTree(sync(batchOf(helpAt("h-live", "2026-11-24T14:59:50Z")), 200)).get("results").get(0);

        assertThat(again.get("status").asText()).isEqualTo("DUPLICATE");
        verify(notifications, never()).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void ifItCannotBeCheckedWhetherTheDelayedRequestIsKeptTheFamilyIsToldAnyway() throws Exception {
        store.failing = true;

        sync(batchOf(helpAt("h-late", "2026-11-24T12:12:00Z")), 503);

        verify(notifications).notifyOnce(8L, "HELP_REQUEST_DELAYED", "edge-h-late", "Pedido de ayuda con retraso", LATE_BODY);
        assertThat(meters.get("tracking.storage.failures").tag("operation", "check_event").counter().count()).isEqualTo(1.0);
    }

    @Test
    void onlyARequestForHelpIsToldAsDelayedAnythingElseKeepsItsType() throws Exception {
        sync(batchOf("{\"eventId\":\"l-late\",\"type\":\"LOW_BATTERY\",\"occurredAt\":\"2026-11-24T12:12:00Z\",\"deviceId\":13,\"batteryPct\":15}"), 200);

        verify(notifications).notifyOnce(8L, "LOW_BATTERY", "edge-l-late", "Batería baja", "Luz de la sala tiene 15% de batería.");
    }

    @Test
    void theSingleEndpointToldsALateRequestAsDelayedToo() throws Exception {
        mvc.perform(post("/api/v1/edge/events").principal(EDGE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"h-single\",\"type\":\"HELP_REQUESTED\",\"occurredAt\":\"2026-11-24T12:12:00Z\",\"detail\":\"ayuda\"}"))
                .andExpect(status().isAccepted());

        verify(notifications).notifyOnce(8L, "HELP_REQUEST_DELAYED", "edge-h-single", "Pedido de ayuda con retraso", LATE_BODY);
    }
}
