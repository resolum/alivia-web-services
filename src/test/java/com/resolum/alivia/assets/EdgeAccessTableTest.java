package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeAccessTable;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        var table = EdgeAccessTable.of(3L, 5L, 7, List.of(
                assignment(9, "AA:BB:CC:DD:EE:09", "DOOR", "cuarto", "Puerta principal"),
                assignment(4, "AA:BB:CC:DD:EE:04", "LIGHT", "sala", "Luz principal")));

        assertEquals(3L, table.installationId());
        assertEquals(5L, table.householdId());
        assertEquals(7, table.version());
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

        var table = EdgeAccessTable.of(3L, 5L, 2, List.of(kept, left));

        assertEquals(List.of(4L), table.nodes().stream().map(node -> node.deviceId()).toList());
    }

    @Test
    void reportsADeviceInMaintenanceAsSoTheEdgeCanDecide() {
        var assignment = assignment(4, "AA:BB:CC:DD:EE:04", "LIGHT", "sala", "Luz");
        assignment.getDevice().changeStatusTo(AssetStatus.MAINTENANCE);

        assertEquals(AssetStatus.MAINTENANCE, EdgeAccessTable.of(3L, 5L, 2, List.of(assignment)).nodes().get(0).status());
    }

    @Test
    void anInstallationWithoutDevicesHasAnEmptyTable() {
        var table = EdgeAccessTable.of(3L, 5L, 0, List.of());

        assertTrue(table.nodes().isEmpty());
        assertEquals(0, table.version());
    }

    @Test
    void theTableCannotBeChangedAfterItIsBuilt() {
        var assignments = new ArrayList<>(List.of(assignment(4, "AA:BB:CC:DD:EE:04", "LIGHT", "sala", "Luz")));
        var table = EdgeAccessTable.of(3L, 5L, 1, assignments);

        assignments.clear();

        assertEquals(1, table.nodes().size());
        assertThrows(UnsupportedOperationException.class, () -> table.nodes().clear());
    }
}
