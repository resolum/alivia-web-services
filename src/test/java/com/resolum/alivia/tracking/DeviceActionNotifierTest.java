package com.resolum.alivia.tracking;

import com.resolum.alivia.tracking.application.internal.eventhandlers.DeviceActionNotifier;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalAssetsService;
import com.resolum.alivia.tracking.application.internal.outboundservices.acl.ExternalNotificationsService;
import com.resolum.alivia.tracking.domain.model.aggregates.DeviceEvent;
import com.resolum.alivia.tracking.domain.model.events.DeviceEventRecordedEvent;
import com.resolum.alivia.tracking.domain.model.valueobjects.CommandAction;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventOrigin;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventResult;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventSeverity;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventType;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceActionNotifierTest {

    private final ExternalAssetsService assets = mock(ExternalAssetsService.class);
    private final ExternalNotificationsService notifications = mock(ExternalNotificationsService.class);
    private final DeviceActionNotifier notifier = new DeviceActionNotifier(assets, notifications, TrackingPolicy.defaults());

    private static DeviceEvent event(EventType type, EventResult result, EventOrigin origin) {
        return new DeviceEvent("e1", 1L, 7L, 8L, 5L, type, EventSeverity.INFORMATIVE, result, origin,
                Instant.parse("2026-11-28T04:43:30Z"), Instant.parse("2026-11-28T04:43:30Z"), "c1", CommandAction.ON,
                null, null, "Por comando de voz", null);
    }

    @Test
    void a_command_that_worked_is_pushed_to_the_relatives_once() {
        when(notifications.relativesOf(8L)).thenReturn(Set.of(39L));
        when(assets.fetchReportingEdge(1L)).thenReturn(Optional.empty());
        notifier.on(new DeviceEventRecordedEvent(event(EventType.COMMAND_RESULT, EventResult.SUCCESS, EventOrigin.LIVE)));
        verify(notifications).notifyOnce(39L, "DEVICE_ACTION", "cmd-1-e1", "Un dispositivo encendida",
                "Por comando de voz, a las 23:43.");
    }

    @Test
    void requests_and_late_events_are_not_pushed() {
        notifier.on(new DeviceEventRecordedEvent(event(EventType.COMMAND_REQUESTED, null, EventOrigin.LIVE)));
        notifier.on(new DeviceEventRecordedEvent(event(EventType.COMMAND_RESULT, EventResult.SUCCESS, EventOrigin.SYNCED)));
        verify(notifications, never()).notifyOnce(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void a_failure_says_what_could_not_be_done() {
        when(notifications.relativesOf(8L)).thenReturn(Set.of(39L));
        when(assets.fetchReportingEdge(1L)).thenReturn(Optional.empty());
        notifier.on(new DeviceEventRecordedEvent(event(EventType.COMMAND_RESULT, EventResult.FAILED_TIMEOUT, EventOrigin.LIVE)));
        verify(notifications).notifyOnce(eq(39L), eq("DEVICE_ACTION"), any(), eq("No se pudo encender un dispositivo"), any());
    }
}
