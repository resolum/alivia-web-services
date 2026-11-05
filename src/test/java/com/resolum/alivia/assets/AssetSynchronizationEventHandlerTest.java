package com.resolum.alivia.assets;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.resolum.alivia.assets.application.internal.eventhandlers.AssetSynchronizationEventHandler;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSynchronizationService;
import com.resolum.alivia.assets.domain.model.events.HouseholdDevicesChangedEvent;
import com.resolum.alivia.assets.domain.model.events.HouseholdDevicesChangedEvent.Cause;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssetSynchronizationEventHandlerTest {

    private static final EdgeAccessTable TABLE = new EdgeAccessTable(3L, 5L, 4, List.of());
    private static final HouseholdDevicesChangedEvent EVENT =
            new HouseholdDevicesChangedEvent(Cause.DEVICE_UNASSIGNED, 5L, 3L, 7L, TABLE);

    private EdgeSynchronizationService sync;
    private AssetSynchronizationEventHandler handler;
    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> logs;
    private boolean loggerWasAdditive;

    @BeforeEach
    void setUp() {
        // The failures these tests provoke are expected: they are captured here instead of printed as errors.
        handlerLogger = (Logger) LoggerFactory.getLogger(AssetSynchronizationEventHandler.class);
        logs = new ListAppender<>();
        logs.start();
        handlerLogger.addAppender(logs);
        loggerWasAdditive = handlerLogger.isAdditive();
        handlerLogger.setAdditive(false);
        sync = mock(EdgeSynchronizationService.class);
        handler = new AssetSynchronizationEventHandler(sync);
    }

    @AfterEach
    void restoreLogging() {
        handlerLogger.detachAppender(logs);
        handlerLogger.setAdditive(loggerWasAdditive);
    }

    @Test
    void tellsTheEdgeServicesOfTheInstallationWithTheCompleteTableAndItsVersion() {
        handler.on(EVENT);

        var captor = ArgumentCaptor.forClass(EdgeSyncMessage.class);
        verify(sync).publish(captor.capture());
        var message = captor.getValue();
        assertEquals(Cause.DEVICE_UNASSIGNED, message.cause());
        assertEquals(3L, message.installationId());
        assertEquals(5L, message.householdId());
        assertEquals(7L, message.deviceId());
        assertSame(TABLE, message.table());
        assertEquals(4, message.table().version());
        assertTrue(logs.list.isEmpty());
    }

    @Test
    void sendsOneMessagePerEvent() {
        handler.on(EVENT);
        handler.on(new HouseholdDevicesChangedEvent(Cause.DEVICE_ASSIGNED, 5L, 3L, 8L,
                new EdgeAccessTable(3L, 5L, 5, List.of())));

        verify(sync, times(2)).publish(any(EdgeSyncMessage.class));
    }

    @Test
    void aFailureToNotifyNeverReachesTheCallerAndIsLogged() {
        doThrow(new IllegalStateException("broker down")).when(sync).publish(any());

        assertDoesNotThrow(() -> handler.on(EVENT));

        assertEquals(1, logs.list.size());
        var entry = logs.list.get(0);
        assertEquals(Level.ERROR, entry.getLevel());
        assertTrue(entry.getFormattedMessage().contains("Could not notify the edge services"));
        assertEquals("broker down", entry.getThrowableProxy().getMessage());
    }
}
