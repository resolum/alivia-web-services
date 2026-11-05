package com.resolum.alivia.assets;

import com.resolum.alivia.assets.application.internal.queryservices.DeviceNetworkQueryServiceImpl;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceNetwork;
import com.resolum.alivia.assets.domain.model.queries.GetEdgeAccessTableByInstallationIdQuery;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceAssignmentRepository;
import com.resolum.alivia.assets.infrastructure.persistence.jpa.repositories.DeviceNetworkRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.InstallationId;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The table of devices that a household keeps for its edge services. */
class EdgeAccessTableTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);

    private static DeviceAssignment assignment(long id, String mac, String type, String room, String function) {
        var device = new Device(mac, "Dispositivo " + id, type, 1L);
        ReflectionTestUtils.setField(device, "id", id);
        return device.assignTo(3L, 5L, room, function, 9L, NOW);
    }

    @Test
    void listsEveryActiveDeviceOrderedByIdWithWhatTheEdgeNeedsToRunIt() {
        var table = EdgeAccessTable.of(3L, 5L, List.of(
                assignment(9, "AA:BB:CC:DD:EE:09", "DOOR", "cuarto", "Puerta principal"),
                assignment(4, "AA:BB:CC:DD:EE:04", "LIGHT", "sala", "Luz principal")));

        assertEquals(3L, table.installationId());
        assertEquals(5L, table.householdId());
        assertEquals(List.of(4L, 9L), table.nodes().stream().map(node -> node.deviceId()).toList());
        var light = table.nodes().get(0);
        assertEquals("AA:BB:CC:DD:EE:04", light.macAddress());
        assertEquals(DeviceType.LIGHT, light.deviceType());
        assertEquals(AssetStatus.IN_USE, light.status());
        assertEquals("sala", light.roomKey());
        assertEquals("Luz principal", light.function());
    }

    @Test
    void leavesOutTheDevicesThatWereUnassigned() {
        var kept = assignment(4, "AA:BB:CC:DD:EE:04", "LIGHT", "sala", "Luz");
        var left = assignment(9, "AA:BB:CC:DD:EE:09", "DOOR", "cuarto", "Puerta");
        left.unassign("Mudanza", 8L, NOW.plusDays(1));

        var table = EdgeAccessTable.of(3L, 5L, List.of(kept, left));

        assertEquals(List.of(4L), table.nodes().stream().map(node -> node.deviceId()).toList());
    }

    @Test
    void reportsADeviceInMaintenanceAsSoTheEdgeCanDecide() {
        var assignment = assignment(4, "AA:BB:CC:DD:EE:04", "LIGHT", "sala", "Luz");
        assignment.getDevice().changeStatusTo(AssetStatus.MAINTENANCE);

        assertEquals(AssetStatus.MAINTENANCE, EdgeAccessTable.of(3L, 5L, List.of(assignment)).nodes().get(0).status());
    }

    @Test
    void anInstallationWithoutDevicesHasAnEmptyTable() {
        assertTrue(EdgeAccessTable.of(3L, 5L, List.of()).nodes().isEmpty());
    }

    @Test
    void theTableCannotBeChangedAfterItIsBuilt() {
        var assignments = new ArrayList<>(List.of(assignment(4, "AA:BB:CC:DD:EE:04", "LIGHT", "sala", "Luz")));
        var table = EdgeAccessTable.of(3L, 5L, assignments);

        assignments.clear();

        assertEquals(1, table.nodes().size());
        assertThrows(UnsupportedOperationException.class, () -> table.nodes().clear());
    }

    @Test
    void theQueryBuildsTheTableOfAKnownInstallationFromItsActiveAssignments() {
        var networks = mock(DeviceNetworkRepository.class);
        var assignments = mock(DeviceAssignmentRepository.class);
        when(networks.findByInstallationId(new InstallationId(3L))).thenReturn(Optional.of(new DeviceNetwork(3L, 5L, 1L)));
        when(assignments.findAllActiveByInstallationId(3L)).thenReturn(
                List.of(assignment(4, "AA:BB:CC:DD:EE:04", "LIGHT", "sala", "Luz")));
        var queries = new DeviceNetworkQueryServiceImpl(networks, assignments);

        var table = queries.handle(new GetEdgeAccessTableByInstallationIdQuery(3L)).orElseThrow();

        assertEquals(5L, table.householdId());
        assertEquals(1, table.nodes().size());
    }

    @Test
    void theQueryAnswersEmptyForAnUnknownInstallationOrOneWithoutHousehold() {
        var networks = mock(DeviceNetworkRepository.class);
        when(networks.findByInstallationId(new InstallationId(3L))).thenReturn(Optional.empty());
        when(networks.findByInstallationId(new InstallationId(4L))).thenReturn(Optional.of(new DeviceNetwork()));
        var queries = new DeviceNetworkQueryServiceImpl(networks, mock(DeviceAssignmentRepository.class));

        assertTrue(queries.handle(new GetEdgeAccessTableByInstallationIdQuery(3L)).isEmpty());
        assertTrue(queries.handle(new GetEdgeAccessTableByInstallationIdQuery(4L)).isEmpty());
    }
}
