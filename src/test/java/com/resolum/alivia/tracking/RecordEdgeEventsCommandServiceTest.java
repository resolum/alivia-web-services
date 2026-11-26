package com.resolum.alivia.tracking;

import com.resolum.alivia.tracking.application.internal.commandservices.RecordEdgeEventsCommandServiceImpl;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService.ReportingEdge;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.exceptions.EdgeNotInInstallation;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.commands.ReportUrgentEventCommand;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventSeverity;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.ReportingContext;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import com.resolum.alivia.tracking.domain.repositories.DeviceEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What an edge reports that cannot wait: who is told, how, and that the event is kept without ever holding the family back.
 * The messages and the source keys are the ones the notifications have always had.
 */
class RecordEdgeEventsCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T15:05:00Z");

    private final ExternalAssetsService assets = mock(ExternalAssetsService.class);
    private final ExternalNotificationsService notifications = mock(ExternalNotificationsService.class);
    private final DeviceEventRepository events = mock(DeviceEventRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private RecordEdgeEventsCommandServiceImpl service;

    @BeforeEach
    void home() {
        service = new RecordEdgeEventsCommandServiceImpl(assets, notifications, events, TrackingPolicy.defaults(),
                Clock.fixed(NOW, ZoneOffset.UTC), meters);
        when(assets.fetchReportingEdge(3L)).thenReturn(Optional.of(new ReportingEdge(
                new ReportingContext(3L, 30L, 7L, Set.of(10L, 11L)), "ACTUATOR", Map.of(10L, "Puerta principal"))));
        when(assets.fetchReportingEdge(4L)).thenReturn(Optional.empty());
        when(notifications.relativesOf(7L)).thenReturn(Set.of(8L));
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
    }

    private static ReportUrgentEventCommand report(String eventId, EventType type, Long deviceId, Integer battery,
                                                   String detail) {
        return new ReportUrgentEventCommand(3L, eventId, type, deviceId, battery, detail, null);
    }

    private DeviceEvent kept() {
        var captor = ArgumentCaptor.forClass(DeviceEvent.class);
        verify(events).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void aHelpRequestReachesTheRelativeInChargeAndIsKept() {
        assertThat(service.handle(report("help-1", EventType.HELP_REQUESTED, null, null, " me caí, ayuda "))).isEqualTo(1);

        verify(notifications).notifyOnce(8L, "HELP_REQUESTED", "edge-help-1", "Solicitud de asistencia",
                "Tu familiar pidió ayuda desde casa: «me caí, ayuda».");
        var event = kept();
        assertThat(event.type()).isEqualTo(EventType.HELP_REQUESTED);
        assertThat(event.severity()).isEqualTo(EventSeverity.CRITICAL);
        assertThat(event.householdId()).isEqualTo(7L);
        assertThat(event.installationId()).isEqualTo(30L);
        assertThat(event.detail()).isEqualTo("me caí, ayuda");
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.receivedAt()).isEqualTo(NOW);
    }

    @Test
    void aHelpRequestWithoutWords() {
        service.handle(report("help-2", EventType.HELP_REQUESTED, null, null, null));

        verify(notifications).notifyOnce(8L, "HELP_REQUESTED", "edge-help-2", "Solicitud de asistencia",
                "Tu familiar pidió ayuda desde casa.");
    }

    @Test
    void namesTheDeviceThatFailedOrRunsOutOfBattery() {
        service.handle(report("f-1", EventType.DEVICE_FAILURE, 10L, null, "sin respuesta del embebido"));
        service.handle(report("f-2", EventType.DEVICE_FAILURE, 11L, null, null));
        service.handle(report("b-1", EventType.LOW_BATTERY, 10L, 15, null));
        service.handle(report("b-2", EventType.LOW_BATTERY, null, null, null));

        verify(notifications).notifyOnce(8L, "ACTUATOR_FAILURE", "edge-f-1", "Falla en un dispositivo",
                "Puerta principal no respondió: sin respuesta del embebido.");
        // The device 11 is in the table but has no name.
        verify(notifications).notifyOnce(8L, "ACTUATOR_FAILURE", "edge-f-2", "Falla en un dispositivo",
                "Un dispositivo de la casa no respondió.");
        verify(notifications).notifyOnce(8L, "LOW_BATTERY", "edge-b-1", "Batería baja",
                "Puerta principal tiene 15% de batería.");
        verify(notifications).notifyOnce(8L, "LOW_BATTERY", "edge-b-2", "Batería baja",
                "Un dispositivo de la casa tiene poca batería.");
    }

    @Test
    void aRepeatedEventNotifiesNobodyAgain() {
        when(notifications.notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThat(service.handle(report("help-1", EventType.HELP_REQUESTED, null, null, null))).isZero();
    }

    @Test
    void anEdgeOutsideAnActiveHomeIsRefusedAndNothingIsKeptOrSaid() {
        assertThatThrownBy(() -> service.handle(new ReportUrgentEventCommand(4L, "x", EventType.HELP_REQUESTED, null,
                null, null, null))).isInstanceOf(EdgeNotInInstallation.class);

        verify(events, never()).insert(any());
        verify(notifications, never()).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aHouseholdWithoutRelativesNotifiesNobodyButTheEventIsKept() {
        when(notifications.relativesOf(7L)).thenReturn(Set.of());

        assertThat(service.handle(report("x", EventType.HELP_REQUESTED, null, null, null))).isZero();

        verify(notifications, never()).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
        assertThat(kept().eventId()).isEqualTo("x");
    }

    @Test
    void theRelativesAreToldBeforeTheEventIsKept() {
        service.handle(report("help-1", EventType.HELP_REQUESTED, null, null, null));

        InOrder order = inOrder(notifications, events);
        order.verify(notifications).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
        order.verify(events).insert(any());
    }

    @Test
    void aFailureToKeepTheEventTellsTheFamilyAnywayAndThenAsksTheEdgeToSendItAgain() {
        when(events.insert(any())).thenThrow(new DataAccessResourceFailureException("MongoDB is down"));

        assertThatThrownBy(() -> service.handle(report("help-1", EventType.HELP_REQUESTED, null, null, "ayuda")))
                .isInstanceOf(com.resolum.alivia.tracking.domain.exceptions.TrackingStorageUnavailable.class);

        verify(notifications).notifyOnce(8L, "HELP_REQUESTED", "edge-help-1", "Solicitud de asistencia",
                "Tu familiar pidió ayuda desde casa: «ayuda».");
        assertThat(meters.counter("tracking.storage.failures", "operation", "insert_event").count()).isEqualTo(1.0);
    }

    @Test
    void aDeviceThatIsNotInTheTableOfTheHomeIsLeftOutInsteadOfRefusingTheReport() {
        service.handle(report("f-9", EventType.DEVICE_FAILURE, 99L, null, "no responde"));

        verify(notifications).notifyOnce(8L, "ACTUATOR_FAILURE", "edge-f-9", "Falla en un dispositivo",
                "Un dispositivo de la casa no respondió: no responde.");
        assertThat(kept().deviceId()).isNull();
    }

    @Test
    void theClockOfTheEdgeIsOnlyTrustedWhenItIsNotAheadOfTheServer() {
        var past = NOW.minus(Duration.ofHours(2));
        service.handle(new ReportUrgentEventCommand(3L, "p", EventType.HELP_REQUESTED, null, null, null, past));
        service.handle(new ReportUrgentEventCommand(3L, "f", EventType.HELP_REQUESTED, null, null, null,
                NOW.plus(Duration.ofHours(2))));

        var captor = ArgumentCaptor.forClass(DeviceEvent.class);
        verify(events, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).occurredAt()).isEqualTo(past);
        assertThat(captor.getAllValues().get(1).occurredAt()).isEqualTo(NOW);
    }

    @Test
    void aLongTextIsCutToTheLimitOfThePolicy() {
        var shortPolicy = new TrackingPolicy(20, 2048, 10, Duration.ofMinutes(5), Duration.ofSeconds(60));
        var shortService = new RecordEdgeEventsCommandServiceImpl(assets, notifications, events, shortPolicy,
                Clock.fixed(NOW, ZoneOffset.UTC), meters);

        shortService.handle(report("t", EventType.HELP_REQUESTED, null, null, "a".repeat(30)));

        assertThat(kept().detail()).hasSize(10);
    }

    @Test
    void theCommandChecksWhatTheEdgeSends() {
        assertThatThrownBy(() -> new ReportUrgentEventCommand(null, "x", EventType.LOW_BATTERY, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportUrgentEventCommand(3L, "con espacios", EventType.LOW_BATTERY, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportUrgentEventCommand(3L, "x", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportUrgentEventCommand(3L, "x", EventType.COMMAND_RESULT, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportUrgentEventCommand(3L, "x", EventType.LOW_BATTERY, null, 101, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ReportUrgentEventCommand(3L, "x", EventType.LOW_BATTERY, null, 5, "  ", null).detail()).isNull();
    }
}
