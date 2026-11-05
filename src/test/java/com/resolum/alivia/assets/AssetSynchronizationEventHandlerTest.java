package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.eventhandlers.AssetSynchronizationEventHandler;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSyncMessage.Type;
import com.resolum.alivia.assets.application.internal.outboundservices.edge.EdgeSynchronizationService;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.aggregates.Edge;
import com.resolum.alivia.assets.domain.model.events.AssetStatusChangedEvent;
import com.resolum.alivia.assets.domain.model.events.AssetUpdatedEvent;
import com.resolum.alivia.assets.domain.model.events.DeviceAssignedEvent;
import com.resolum.alivia.assets.domain.model.events.DeviceUnassignedEvent;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetKind;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.domain.services.DeviceNetworkQueryService;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssetSynchronizationEventHandlerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 10, 0);

    private DeviceAssignmentRepository assignments;
    private DeviceNetworkRepository networks;
    private DeviceNetworkQueryService networkQueries;
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

        assignments = mock(DeviceAssignmentRepository.class);
        networks = mock(DeviceNetworkRepository.class);
        networkQueries = mock(DeviceNetworkQueryService.class);
        sync = mock(EdgeSynchronizationService.class);
        handler = new AssetSynchronizationEventHandler(assignments, networks, networkQueries, sync);
    }

    @AfterEach
    void restoreLogging() {
        handlerLogger.detachAppender(logs);
        handlerLogger.setAdditive(loggerWasAdditive);
    }

    private static Device device() {
        var device = new Device("AA:BB:CC:DD:EE:FF", "Luz", "LIGHT", 1L);
        ReflectionTestUtils.setField(device, "id", 7L);
        return device;
    }

    @SuppressWarnings("unchecked")
    private static <E> E announced(DeviceAssignment assignment, Class<E> type) {
        var events = (java.util.Collection<Object>) ReflectionTestUtils.invokeMethod(assignment, "domainEvents");
        return events.stream().filter(type::isInstance).map(type::cast).reduce((first, last) -> last).orElseThrow();
    }

    private static DeviceAssignment assignmentOf(Device device) {
        var assignment = device.assignTo(3L, 5L, "sala", "Luz principal", 9L, NOW);
        ReflectionTestUtils.setField(assignment, "id", 21L);
        return assignment;
    }

    /** The table the household has after the change: the active assignments of the installation. */
    private void tableOfInstallation3Is(DeviceAssignment... active) {
        var table = EdgeAccessTable.of(3L, 5L, List.of(active));
        when(networkQueries.handle(new GetEdgeAccessTableByInstallationIdQuery(3L))).thenReturn(Optional.of(table));
    }

    private EdgeSyncMessage published() {
        var captor = ArgumentCaptor.forClass(EdgeSyncMessage.class);
        verify(sync).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void anAssignmentTellsTheInstallationWhereTheDeviceWasPlaced() {
        var assignment = assignmentOf(device());
        tableOfInstallation3Is(assignment);
        handler.on(announced(assignment, DeviceAssignedEvent.class));

        var message = published();
        assertEquals(Type.DEVICE_ASSIGNED, message.type());
        assertEquals(3L, message.installationId());
        assertEquals(5L, message.householdId());
        assertEquals(AssetKind.DEVICE, message.assetKind());
        assertEquals(7L, message.assetId());
        assertEquals("AA:BB:CC:DD:EE:FF", message.macAddress());
        assertEquals("LIGHT", message.assetType());
        assertEquals("IN_USE", message.status());
        assertEquals("sala", message.roomKey());
        assertEquals("Luz principal", message.function());
        assertEquals(1, message.table().nodes().size());
        assertEquals(7L, message.table().nodes().get(0).deviceId());
    }

    @Test
    void everyMessageCarriesTheCompleteTableOfTheHouseholdNotOnlyTheChange() {
        var first = assignmentOf(device());
        var otherDevice = new Device("AA:BB:CC:DD:EE:11", "Puerta", "DOOR", 1L);
        ReflectionTestUtils.setField(otherDevice, "id", 8L);
        var second = otherDevice.assignTo(3L, 5L, "cuarto", "Puerta principal", 9L, NOW);
        tableOfInstallation3Is(second, first);

        handler.on(announced(second, DeviceAssignedEvent.class));

        var table = published().table();
        assertEquals(3L, table.installationId());
        assertEquals(5L, table.householdId());
        assertEquals(List.of(7L, 8L), table.nodes().stream().map(node -> node.deviceId()).toList());
        assertEquals("cuarto", table.nodes().get(1).roomKey());
    }

    @Test
    void aMessageForAnUnknownInstallationStillGoesOutWithoutATable() {
        when(networkQueries.handle(any(GetEdgeAccessTableByInstallationIdQuery.class))).thenReturn(Optional.empty());

        handler.on(announced(assignmentOf(device()), DeviceAssignedEvent.class));

        assertNull(published().table());
    }

    @Test
    void anUnassignmentTellsTheInstallationTheDeviceLeftAndNeverTheFreeTextReason() {
        var device = device();
        var assignment = assignmentOf(device);
        assignment.unassign("Información sensible escrita por una persona", 8L, NOW.plusDays(1));
        tableOfInstallation3Is(assignment);

        handler.on(announced(assignment, DeviceUnassignedEvent.class));

        var message = published();
        assertEquals(Type.DEVICE_UNASSIGNED, message.type());
        assertEquals(3L, message.installationId());
        assertEquals("AVAILABLE", message.status());
        assertFalse(message.toString().contains("sensible"));
        assertTrue(message.table().nodes().isEmpty(), "the device left, so the table no longer lists it");
    }

    @Test
    void aStatusChangeOfAnAssignedDeviceTellsItsInstallation() {
        var device = device();
        var assignment = assignmentOf(device);
        when(assignments.findActiveByDeviceId(7L)).thenReturn(Optional.of(assignment));
        device.changeStatusTo(AssetStatus.BROKEN);

        handler.on(new AssetStatusChangedEvent(AssetKind.DEVICE, 7L, "AA:BB:CC:DD:EE:FF", "LIGHT", AssetStatus.IN_USE, AssetStatus.BROKEN));

        var message = published();
        assertEquals(Type.ASSET_STATUS_CHANGED, message.type());
        assertEquals(3L, message.installationId());
        assertEquals("BROKEN", message.status());
        assertEquals("sala", message.roomKey());
    }

    @Test
    void anUpdateOfAnAssignedDeviceTellsItsInstallation() {
        var device = device();
        when(assignments.findActiveByDeviceId(7L)).thenReturn(Optional.of(assignmentOf(device)));

        handler.on(new AssetUpdatedEvent(AssetKind.DEVICE, 7L, "AA:BB:CC:DD:EE:FF", "LIGHT", AssetStatus.IN_USE));

        assertEquals(Type.ASSET_UPDATED, published().type());
    }

    @Test
    void aDeviceThatIsNotPlacedAnywhereHasNoEdgeServicesToTell() {
        when(assignments.findActiveByDeviceId(7L)).thenReturn(Optional.empty());

        handler.on(new AssetStatusChangedEvent(AssetKind.DEVICE, 7L, "AA:BB:CC:DD:EE:FF", "LIGHT", AssetStatus.AVAILABLE, AssetStatus.MAINTENANCE));
        handler.on(new AssetUpdatedEvent(AssetKind.DEVICE, 7L, "AA:BB:CC:DD:EE:FF", "LIGHT", AssetStatus.AVAILABLE));

        verifyNoInteractions(sync);
    }

    @Test
    void anEdgeTellsEveryInstallationThatUsesIt() {
        var edge = new Edge(1L, "AA:BB:CC:DD:EE:00", "ACTUATOR_EDGE");
        ReflectionTestUtils.setField(edge, "id", 4L);
        when(networks.findAllByEdgeId(4L)).thenReturn(List.of(new DeviceNetwork(3L, 5L, 1L), new DeviceNetwork(8L, 6L, 1L)));

        handler.on(new AssetUpdatedEvent(AssetKind.EDGE, 4L, "AA:BB:CC:DD:EE:00", "ACTUATOR_EDGE", AssetStatus.AVAILABLE));

        var captor = ArgumentCaptor.forClass(EdgeSyncMessage.class);
        verify(sync, times(2)).publish(captor.capture());
        assertEquals(List.of(3L, 8L), captor.getAllValues().stream().map(EdgeSyncMessage::installationId).toList());
        assertTrue(captor.getAllValues().stream().allMatch(message -> message.assetKind() == AssetKind.EDGE));
        assertEquals("ACTUATOR_EDGE", captor.getAllValues().get(0).assetType());
    }

    @Test
    void anEdgeInNoInstallationHasNobodyToTell() {
        var edge = new Edge(1L, "AA:BB:CC:DD:EE:00", "ACTUATOR_EDGE");
        ReflectionTestUtils.setField(edge, "id", 4L);
        when(networks.findAllByEdgeId(4L)).thenReturn(List.of());

        handler.on(new AssetStatusChangedEvent(AssetKind.EDGE, 4L, "AA:BB:CC:DD:EE:00", "ACTUATOR_EDGE", AssetStatus.AVAILABLE, AssetStatus.MAINTENANCE));

        verifyNoInteractions(sync);
    }

    @Test
    void aFailureToNotifyNeverReachesTheCaller() {
        doThrow(new IllegalStateException("broker down")).when(sync).publish(any());

        assertDoesNotThrow(() -> handler.on(announced(assignmentOf(device()), DeviceAssignedEvent.class)));

        assertErrorLogged("broker down");
    }

    @Test
    void aFailureToFindTheInstallationNeverReachesTheCallerEither() {
        when(assignments.findActiveByDeviceId(any())).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> handler.on(new AssetUpdatedEvent(AssetKind.DEVICE, 7L, "AA:BB:CC:DD:EE:FF", "LIGHT", AssetStatus.AVAILABLE)));
        verifyNoInteractions(sync);
        assertErrorLogged("database down");
    }

    private void assertErrorLogged(String cause) {
        assertEquals(1, logs.list.size());
        var entry = logs.list.get(0);
        assertEquals(Level.ERROR, entry.getLevel());
        assertTrue(entry.getFormattedMessage().contains("Could not notify the edge services"));
        assertEquals(cause, entry.getThrowableProxy().getMessage());
    }
}
