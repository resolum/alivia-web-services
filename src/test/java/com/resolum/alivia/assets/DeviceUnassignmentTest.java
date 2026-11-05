package com.resolum.alivia.assets;

import com.resolum.alivia.assets.domain.exceptions.DeviceNotAssigned;
import com.resolum.alivia.assets.domain.model.aggregates.Device;
import com.resolum.alivia.assets.domain.model.aggregates.DeviceAssignment;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DeviceUnassignmentTest {

    private static final LocalDateTime ASSIGNED_AT = LocalDateTime.of(2026, 11, 4, 10, 0);
    private static final LocalDateTime UNASSIGNED_AT = LocalDateTime.of(2026, 11, 20, 16, 30);

    private static Device device() {
        var device = new Device("AA:BB:CC:DD:EE:FF", "Luz de la sala", "LIGHT", 1L);
        ReflectionTestUtils.setField(device, "id", 7L);
        return device;
    }

    private static DeviceAssignment assignment(Device device) {
        return device.assignTo(3L, 5L, "sala", "Luz principal", 9L, ASSIGNED_AT);
    }

    @Test
    void closesTheAssignmentAndLeavesTheDeviceAvailable() {
        var device = device();
        var assignment = assignment(device);

        assignment.unassign("  Reemplazo por falla del equipo ", 8L, UNASSIGNED_AT);

        assertFalse(assignment.isActive());
        assertEquals(UNASSIGNED_AT, assignment.getUnassignedAt());
        assertEquals(8L, assignment.getUnassignedBy());
        assertEquals("Reemplazo por falla del equipo", assignment.getUnassignReason());
        assertEquals(AssetStatus.AVAILABLE, device.getAssetStatus());
        assertTrue(device.isAvailable());
    }

    @Test
    void keepsEveryFieldOfTheAssignmentInTheHistory() {
        var device = device();
        var assignment = assignment(device);

        assignment.unassign("Fin del servicio", 8L, UNASSIGNED_AT);

        assertSame(device, assignment.getDevice());
        assertEquals(3L, assignment.getInstallationId());
        assertEquals(5L, assignment.getHouseholdId());
        assertEquals("sala", assignment.getRoomKey());
        assertEquals("Luz principal", assignment.getFunction());
        assertEquals(9L, assignment.getAssignedBy());
        assertEquals(ASSIGNED_AT, assignment.getAssignedAt());
    }

    @Test
    void cannotBeUnassignedTwice() {
        var device = device();
        var assignment = assignment(device);
        assignment.unassign("Fin del servicio", 8L, UNASSIGNED_AT);

        var exception = assertThrows(DeviceNotAssigned.class,
                () -> assignment.unassign("Otro motivo", 4L, UNASSIGNED_AT.plusDays(1)));

        assertTrue(exception.getMessage().contains("device with id 7"));
        assertEquals(UNASSIGNED_AT, assignment.getUnassignedAt());
        assertEquals(8L, assignment.getUnassignedBy());
        assertEquals("Fin del servicio", assignment.getUnassignReason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void requiresAReason(String reason) {
        var device = device();
        var assignment = assignment(device);

        assertThrows(IllegalArgumentException.class, () -> assignment.unassign(reason, 8L, UNASSIGNED_AT));
        assertThrows(IllegalArgumentException.class, () -> assignment.unassign(null, 8L, UNASSIGNED_AT));

        assertStillAssigned(assignment, device);
    }

    @Test
    void limitsTheLengthOfTheReason() {
        var device = device();
        var assignment = assignment(device);

        assertThrows(IllegalArgumentException.class,
                () -> assignment.unassign("a".repeat(DeviceAssignment.UNASSIGN_REASON_MAX_LENGTH + 1), 8L, UNASSIGNED_AT));

        assertStillAssigned(assignment, device);
        assertDoesNotThrow(() -> assignment.unassign("a".repeat(DeviceAssignment.UNASSIGN_REASON_MAX_LENGTH), 8L, UNASSIGNED_AT));
    }

    @Test
    void doesNotChangeAnythingWhenTheActorOrTheDateAreInvalid() {
        var device = device();
        var assignment = assignment(device);

        assertThrows(IllegalArgumentException.class, () -> assignment.unassign("Motivo", null, UNASSIGNED_AT));
        assertThrows(IllegalArgumentException.class, () -> assignment.unassign("Motivo", 0L, UNASSIGNED_AT));
        assertThrows(IllegalArgumentException.class, () -> assignment.unassign("Motivo", 8L, null));
        assertThrows(IllegalArgumentException.class, () -> assignment.unassign("Motivo", 8L, ASSIGNED_AT.minusSeconds(1)));

        assertStillAssigned(assignment, device);
        assertDoesNotThrow(() -> assignment.unassign("Motivo", 8L, ASSIGNED_AT));
    }

    @ParameterizedTest
    @EnumSource(value = AssetStatus.class, names = {"MAINTENANCE", "BROKEN"})
    void doesNotRepairADeviceThatLeftUseWhileAssigned(AssetStatus status) {
        var device = device();
        var assignment = assignment(device);
        device.changeStatusTo(status);

        assignment.unassign("Se retira para revisión", 8L, UNASSIGNED_AT);

        assertFalse(assignment.isActive());
        assertEquals(status, device.getAssetStatus());
    }

    @Test
    void theDeviceCanBePlacedAgainAfterBeingUnassigned() {
        var device = device();
        var first = assignment(device);
        first.unassign("Mudanza", 8L, UNASSIGNED_AT);

        var second = device.assignTo(4L, 6L, "cuarto", "Luz de noche", 9L, UNASSIGNED_AT.plusDays(2));

        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());
        assertTrue(second.isActive());
        assertFalse(first.isActive());
        assertEquals(5L, first.getHouseholdId());
        assertEquals(6L, second.getHouseholdId());
    }

    private static void assertStillAssigned(DeviceAssignment assignment, Device device) {
        assertTrue(assignment.isActive());
        assertNull(assignment.getUnassignedBy());
        assertNull(assignment.getUnassignReason());
        assertEquals(AssetStatus.IN_USE, device.getAssetStatus());
    }
}
